package com.example.myandroidapp;

import android.content.Intent;
import android.net.VpnService;
import android.os.IBinder;
import android.util.Log;
import android.os.ParcelFileDescriptor;
import java.io.IOException;
import java.io.FileInputStream;
import androidx.localbroadcastmanager.content.LocalBroadcastManager; // Added import
// android.content.Intent is already imported

public class MyVpnService extends VpnService {

    public static final String ACTION_HTTP_REQUEST_CAPTURED = "com.example.myandroidapp.HTTP_REQUEST_CAPTURED"; // Added action
    public static final String EXTRA_HTTP_REQUEST_INFO = "com.example.myandroidapp.EXTRA_HTTP_REQUEST_INFO"; // Added extra key

    private static final String TAG = "MyVpnService";
    private Thread vpnThread;
    private ParcelFileDescriptor vpnInterface = null; // Store the VPN interface

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        // Logic to start the VPN will be added here later
        // For now, we can call a placeholder startVpn() method
        startVpn();
        return START_STICKY; // Or START_NOT_STICKY depending on desired behavior
    }

    public void startVpn() {
        Log.d(TAG, "startVpn() called");
        if (vpnThread != null && vpnThread.isAlive()) {
            Log.d(TAG, "VPN thread already running");
            return;
        }

        Builder builder = new Builder();
        try {
            builder.setSession("MyAndroidAppVpn")
                   .addAddress("10.0.0.2", 24) // VPN interface IP address and prefix
                   .addRoute("0.0.0.0", 0)    // Route all IPv4 traffic through VPN
                   .addDnsServer("8.8.8.8")     // Google DNS server
                   // .addAllowedApplication("com.example.anotherapp") // Optional: only tunnel specific app
                   // For MTU, it's often good to set it, e.g., 1500, but depends on network.
                   // Not setting it explicitly for now to keep it simple.
                   .setMtu(1500); // A common MTU value

            vpnInterface = builder.establish();

            if (vpnInterface == null) {
                Log.e(TAG, "VpnService.Builder.establish() returned null. VPN not established.");
                // Potentially notify UI or handle error
                stopSelf(); // Stop service if VPN cannot be established
                return;
            }

            Log.d(TAG, "VPN interface established.");

            // Start the packet reading thread
            vpnThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Log.d(TAG, "VPN thread started.");
                        // Packets to be read from this input stream
                        FileInputStream inputStream = new FileInputStream(vpnInterface.getFileDescriptor());
                        // Buffer to hold outgoing packets
                        // FileOutputStream outputStream = new FileOutputStream(vpnInterface.getFileDescriptor()); // For writing back, if needed later

                        byte[] packet = new byte[32767]; // Max packet size
                        while (!Thread.interrupted()) {
                            int length = inputStream.read(packet);
                            if (length > 0) {
                                Log.d(TAG, "Read packet, length: " + length);

                                // Basic IP Header Parsing (IPv4)
                                // Ensure packet is long enough for a minimal IP header (20 bytes)
                                if (length < 20) {
                                    Log.w(TAG, "Packet too short for IP header: " + length);
                                    continue;
                                }

                                // IP Version (byte 0, bits 7-4)
                                int ipVersion = (packet[0] >> 4) & 0x0F;
                                if (ipVersion != 4) {
                                    Log.d(TAG, "Non-IPv4 packet received, version: " + ipVersion + ". Skipping.");
                                    continue;
                                }

                                // IP Header Length (byte 0, bits 3-0) in 32-bit words
                                int ipHeaderLength = (packet[0] & 0x0F) * 4; // In bytes
                                if (length < ipHeaderLength) {
                                    Log.w(TAG, "Packet too short for IP header length: " + length + ", expected: " + ipHeaderLength);
                                    continue;
                                }

                                // Protocol (byte 9)
                                int protocol = packet[9] & 0xFF; // Read as unsigned byte

                                // For now, we only care about TCP (protocol number 6)
                                if (protocol == 6) { // TCP
                                    // Log.d(TAG, "TCP Packet received.");

                                    // Basic TCP Header Parsing
                                    // Ensure packet is long enough for even a minimal TCP header (20 bytes from IP header end)
                                    if (length < ipHeaderLength + 20) {
                                        Log.w(TAG, "Packet too short for minimal TCP header, length: " + length + ", ipHeaderLength: " + ipHeaderLength);
                                        continue;
                                    }

                                    // TCP Data Offset is at byte (ipHeaderLength + 12). Ensure this byte exists.
                                    if (length < ipHeaderLength + 13) { // Need at least up to byte 12 of TCP header (0-indexed)
                                        Log.w(TAG, "Packet too short for TCP Data Offset byte, length: " + length + ", ipHeaderLength: " + ipHeaderLength);
                                        continue;
                                    }
                                    int tcpDataOffset = (packet[ipHeaderLength + 12] >> 4) & 0x0F;
                                    int tcpHeaderLength = tcpDataOffset * 4; // TCP header length in bytes

                                    // Ensure the calculated TCP header length is within packet bounds
                                    if (ipHeaderLength + tcpHeaderLength > length) {
                                        Log.w(TAG, "Calculated TCP header extends beyond packet length. ipHL: " + ipHeaderLength + ", tcpHL: " + tcpHeaderLength + ", packetL: " + length);
                                        continue;
                                    }

                                    // Source Port (bytes at ipHeaderLength + 0 and ipHeaderLength + 1)
                                    int sourcePort = ((packet[ipHeaderLength] & 0xFF) << 8) | (packet[ipHeaderLength + 1] & 0xFF);
                                    // Destination Port (bytes at ipHeaderLength + 2 and ipHeaderLength + 3)
                                    int destinationPort = ((packet[ipHeaderLength + 2] & 0xFF) << 8) | (packet[ipHeaderLength + 3] & 0xFF);

                                    // Log.d(TAG, "TCP Packet: Src Port=" + sourcePort + ", Dst Port=" + destinationPort);

                                    if (destinationPort == 80) {
                                        Log.i(TAG, "HTTP traffic detected (TCP port 80): " + sourcePort + " -> " + destinationPort);

                                        // Calculate TCP Header Length (Data Offset)
                                        // Calculate payload start and length
                                        int payloadOffset = ipHeaderLength + tcpHeaderLength;
                                        int payloadLength = length - payloadOffset;

                                        if (payloadLength > 0) {
                                            // Ensure payloadOffset is within bounds (it should be if previous checks passed, but good for safety)
                                            if (payloadOffset > length) {
                                                 Log.w(TAG, "Calculated payload offset is beyond packet length: " + payloadOffset + ", packetL: " + length);
                                                 continue;
                                            }
                                            // Ensure payloadLength doesn't exceed packet bounds from payloadOffset
                                            if (payloadOffset + payloadLength > length) {
                                                Log.w(TAG, "Calculated payload extends beyond packet length. offset: " + payloadOffset + ", payloadL: " + payloadLength + ", packetL: " + length);
                                                // Adjust payloadLength to fit if necessary, or log and continue
                                                payloadLength = length - payloadOffset;
                                                if (payloadLength <=0) continue;
                                            }

                                            try {
                                                // Convert the TCP payload to a String.
                                                // Assuming UTF-8, which is common for HTTP headers.
                                                String httpPayload = new String(packet, payloadOffset, payloadLength, "UTF-8");

                                                // Split into lines. HTTP headers are separated by CRLF ("\r\n").
                                                String[] lines = httpPayload.split("\r\n");

                                                if (lines.length > 0) {
                                                    // First line is the Request Line (e.g., "GET /path HTTP/1.1")
                                                    String requestLine = lines[0];
                                                    Log.d(TAG, "HTTP Request Line: " + requestLine);

                                                    // Parse Request Line (basic split by space)
                                                    String[] requestParts = requestLine.split(" ");
                                                    if (requestParts.length >= 2) { // Method and URI are most important
                                                        String method = requestParts[0];
                                                        String uri = requestParts[1];
                                                        String httpVersion = (requestParts.length > 2) ? requestParts[2] : "Unknown";
                                                        // Log.i(TAG, "HTTP Method: " + method + ", URI: " + uri + ", Version: " + httpVersion); // Original log

                                                        String currentHost = null;
                                                        // Subsequent lines are headers (until a blank line)
                                                        for (int i = 1; i < lines.length; i++) {
                                                            if (lines[i].isEmpty()) {
                                                                // Blank line indicates end of headers
                                                                break;
                                                            }
                                                            // Log first few headers for debugging
                                                            if (i < 5) { // Log up to 4 headers
                                                                 Log.d(TAG, "HTTP Header: " + lines[i]);
                                                            }
                                                            if (lines[i].toLowerCase().startsWith("host:")) {
                                                                currentHost = lines[i].substring(5).trim();
                                                                // Log.i(TAG, "HTTP Host: " + currentHost); // Original log
                                                            }
                                                        }

                                                        // Broadcast the captured info
                                                        HttpRequestInfo info = new HttpRequestInfo(method, uri, httpVersion, currentHost);
                                                        Intent broadcastIntent = new Intent(ACTION_HTTP_REQUEST_CAPTURED);
                                                        broadcastIntent.putExtra(EXTRA_HTTP_REQUEST_INFO, info);
                                                        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);
                                                        Log.d(TAG, "Broadcast sent for: " + info.toString());

                                                    }
                                                }
                                            } catch (java.io.UnsupportedEncodingException e) {
                                                Log.e(TAG, "Unsupported encoding for HTTP payload", e);
                                            } catch (Exception e) {
                                                // Catching general exceptions if string manipulation or array access fails
                                                Log.e(TAG, "Error parsing HTTP payload: " + e.getMessage(), e);
                                            }
                                        } else {
                                            Log.d(TAG, "HTTP packet with no payload (e.g., TCP ACK for HTTP traffic)");
                                        }
                                        // End of HTTP parsing
                                    } else if (destinationPort == 443) {
                                        Log.i(TAG, "HTTPS traffic detected (TCP port 443): " + sourcePort + " -> " + destinationPort);
                                        // SNI extraction or other HTTPS metadata handling will go here
                                    } else {
                                        // Log.d(TAG, "Other TCP traffic: " + sourcePort + " -> " + destinationPort);
                                    }

                                } else if (protocol == 17) { // UDP
                                    // Log.d(TAG, "UDP Packet received. Skipping for now.");
                                } else if (protocol == 1) { // ICMP
                                    // Log.d(TAG, "ICMP Packet received. Skipping for now.");
                                } else {
                                    // Log.d(TAG, "Other protocol: " + protocol + ". Skipping.");
                                }

                                // Placeholder for packet processing/forwarding
                                // For a simple "pass-through" VPN (not analyzing yet), you might write it to outputStream
                                // outputStream.write(packet, 0, length);
                            }
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Error in VPN thread (reading/writing packets): " + e.getMessage(), e);
                    } catch (Exception e) {
                        Log.e(TAG, "Generic error in VPN thread: " + e.getMessage(), e);
                    } finally {
                        Log.d(TAG, "VPN thread stopping.");
                        // Ensure resources are cleaned up if the thread exits unexpectedly
                        // stopVpn(); // Calling stopVpn() from here might be problematic if already stopping.
                                   // Consider a different way to signal service to stop or clean up.
                                   // For now, just log. Service stop is handled by onRevoke or explicit call.
                    }
                }
            }, "MyVpnThread"); // Giving the thread a name is good for debugging

            vpnThread.start();
            Log.d(TAG, "VPN thread initiated.");

        } catch (Exception e) {
            Log.e(TAG, "Error establishing VPN: " + e.getMessage(), e);
            // Clean up and stop service
            if (vpnInterface != null) {
                try {
                    vpnInterface.close();
                } catch (IOException ex) {
                    Log.e(TAG, "Error closing vpnInterface: " + ex.getMessage(), ex);
                }
                vpnInterface = null;
            }
            stopSelf();
        }
    }

    public void stopVpn() {
        Log.d(TAG, "stopVpn() called");
        // Close the VPN interface first, this should help the thread to exit if blocked on read
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
                Log.d(TAG, "VPN interface closed.");
            } catch (IOException e) {
                Log.e(TAG, "Error closing vpnInterface: " + e.getMessage(), e);
            }
            vpnInterface = null;
        }

        // Stop the VPN thread
        if (vpnThread != null) {
            Log.d(TAG, "Interrupting VPN thread.");
            vpnThread.interrupt();
            try {
                vpnThread.join(1000); // Wait for thread to die for up to 1 second
                if (vpnThread.isAlive()) {
                    Log.w(TAG, "VPN thread did not terminate after 1s join.");
                } else {
                    Log.d(TAG, "VPN thread joined.");
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Failed to join VPN thread: " + e.getMessage(), e);
                Thread.currentThread().interrupt(); // Preserve interrupt status
            }
            vpnThread = null;
        }

        // Any other cleanup for stopping the VPN
        stopSelf(); // Stops the service
    }

    @Override
    public void onRevoke() {
        Log.d(TAG, "onRevoke");
        stopVpn(); // VPN permission revoked by user or system
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        stopVpn(); // Ensure VPN is stopped when service is destroyed
        super.onDestroy();
    }

    // onBind is not typically used for VpnService unless you need complex interaction.
    // For starting/stopping, Intents are common.
    @Override
    public IBinder onBind(Intent intent) {
        return null; // We are not binding to this service from other components.
    }
}
