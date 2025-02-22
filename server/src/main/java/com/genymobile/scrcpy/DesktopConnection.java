package com.genymobile.scrcpy;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class DesktopConnection implements Closeable {

    private static final int DEVICE_NAME_FIELD_LENGTH = 64;

    private static final String SOCKET_NAME_PREFIX = "scrcpy";

    private final LocalSocket videoSocket;
    private final FileDescriptor videoFd;

    private final LocalSocket audioSocket;
    private final FileDescriptor audioFd;

    private final LocalSocket sizeInfoSocket;
    private final FileDescriptor sizeInfoFd;

    private final LocalSocket controlSocket;
    private final InputStream controlInputStream;
    private final OutputStream controlOutputStream;

    private final ControlMessageReader reader = new ControlMessageReader();
    private final DeviceMessageWriter writer = new DeviceMessageWriter();

    private DesktopConnection(LocalSocket videoSocket, LocalSocket audioSocket, LocalSocket controlSocket, LocalSocket sizeInfoSocket) throws IOException {
        this.videoSocket = videoSocket;
        this.controlSocket = controlSocket;
        this.audioSocket = audioSocket;
        this.sizeInfoSocket = sizeInfoSocket;
        if (controlSocket != null) {
            controlInputStream = controlSocket.getInputStream();
            controlOutputStream = controlSocket.getOutputStream();
        } else {
            controlInputStream = null;
            controlOutputStream = null;
        }
        videoFd = videoSocket != null ? videoSocket.getFileDescriptor() : null;
        audioFd = audioSocket != null ? audioSocket.getFileDescriptor() : null;
        sizeInfoFd = sizeInfoSocket != null ? sizeInfoSocket.getFileDescriptor() : null;
    }

    private static LocalSocket connect(String abstractName) throws IOException {
        LocalSocket localSocket = new LocalSocket();
        localSocket.connect(new LocalSocketAddress(abstractName));
        return localSocket;
    }

    private static String getSocketName(int scid) {
        if (scid == -1) {
            // If no SCID is set, use "scrcpy" to simplify using scrcpy-server alone
            return SOCKET_NAME_PREFIX;
        }

        return SOCKET_NAME_PREFIX + String.format("_%08x", scid);
    }

    public static DesktopConnection open(int scid, boolean tunnelForward, boolean video, boolean audio, boolean control, boolean rotation, boolean sendDummyByte)
            throws IOException {
        String socketName = getSocketName(scid);

        LocalSocket videoSocket = null;
        LocalSocket audioSocket = null;
        LocalSocket controlSocket = null;
        LocalSocket rotationSocket = null;
        try {
            if (tunnelForward) {
                try (LocalServerSocket localServerSocket = new LocalServerSocket(socketName)) {
                    if (video) {
                        videoSocket = localServerSocket.accept();
                        if (sendDummyByte) {
                            // send one byte so the client may read() to detect a connection error
                            videoSocket.getOutputStream().write(0);
                            sendDummyByte = false;
                        }
                    }
                    if (audio) {
                        audioSocket = localServerSocket.accept();
                        if (sendDummyByte) {
                            // send one byte so the client may read() to detect a connection error
                            audioSocket.getOutputStream().write(0);
                            sendDummyByte = false;
                        }
                    }
                    if (control) {
                        controlSocket = localServerSocket.accept();
                        if (sendDummyByte) {
                            // send one byte so the client may read() to detect a connection error
                            controlSocket.getOutputStream().write(0);
                            sendDummyByte = false;
                        }
                    }
                    if (rotation) {
                        rotationSocket = localServerSocket.accept();
                        if (sendDummyByte) {
                            // send one byte so the client may read() to detect a connection error
                            rotationSocket.getOutputStream().write(0);
                            sendDummyByte = false;
                        }
                    }
                }
            } else {
                if (video) {
                    videoSocket = connect(socketName);
                }
                if (audio) {
                    audioSocket = connect(socketName);
                }
                if (control) {
                    controlSocket = connect(socketName);
                }
                if (rotation) {
                    rotationSocket = connect(socketName);
                }
            }
        } catch (IOException | RuntimeException e) {
            if (videoSocket != null) {
                videoSocket.close();
            }
            if (audioSocket != null) {
                audioSocket.close();
            }
            if (controlSocket != null) {
                controlSocket.close();
            }
            if (rotationSocket != null) {
                rotationSocket.close();
            }
            throw e;
        }

        return new DesktopConnection(videoSocket, audioSocket, controlSocket, rotationSocket);
    }

    private LocalSocket getFirstSocket() {
        if (videoSocket != null) {
            return videoSocket;
        }
        if (audioSocket != null) {
            return audioSocket;
        }
        if (sizeInfoSocket != null) {
            return sizeInfoSocket;
        }
        return controlSocket;
    }

    public void shutdown() throws IOException {
        if (videoSocket != null) {
            videoSocket.shutdownInput();
            videoSocket.shutdownOutput();
        }
        if (audioSocket != null) {
            audioSocket.shutdownInput();
            audioSocket.shutdownOutput();
        }
        if (controlSocket != null) {
            controlSocket.shutdownInput();
            controlSocket.shutdownOutput();
        }
        if (sizeInfoSocket != null) {
            sizeInfoSocket.shutdownInput();
            sizeInfoSocket.shutdownOutput();
        }
    }

    public void close() throws IOException {
        if (videoSocket != null) {
            videoSocket.close();
        }
        if (audioSocket != null) {
            audioSocket.close();
        }
        if (controlSocket != null) {
            controlSocket.close();
        }
        if (sizeInfoSocket != null) {
            sizeInfoSocket.close();
        }
    }

    public void sendDeviceMeta(String deviceName) throws IOException {
        byte[] buffer = new byte[DEVICE_NAME_FIELD_LENGTH];

        byte[] deviceNameBytes = deviceName.getBytes(StandardCharsets.UTF_8);
        int len = StringUtils.getUtf8TruncationIndex(deviceNameBytes, DEVICE_NAME_FIELD_LENGTH - 1);
        System.arraycopy(deviceNameBytes, 0, buffer, 0, len);
        // byte[] are always 0-initialized in java, no need to set '\0' explicitly

        FileDescriptor fd = getFirstSocket().getFileDescriptor();
        IO.writeFully(fd, buffer, 0, buffer.length);
    }

    public FileDescriptor getVideoFd() {
        return videoFd;
    }

    public FileDescriptor getSizeInfoFd() {
        return sizeInfoFd;
    }

    public FileDescriptor getAudioFd() {
        return audioFd;
    }

    public ControlMessage receiveControlMessage() throws IOException {
        ControlMessage msg = reader.next();
        while (msg == null) {
            reader.readFrom(controlInputStream);
            msg = reader.next();
        }
        return msg;
    }

    public void sendDeviceMessage(DeviceMessage msg) throws IOException {
        writer.writeTo(msg, controlOutputStream);
    }
}
