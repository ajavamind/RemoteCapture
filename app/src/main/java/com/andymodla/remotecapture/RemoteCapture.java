package com.andymodla.remotecapture;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import netP5.UdpClient;
import processing.core.PApplet;

// Processing libraries imports
//import oscP5.*; // does not use this part of oscP5 library

public class RemoteCapture extends PApplet {

// Creates a broadcast client that sends focus/shutter key commands to NX500 cameras
// requires Internet Android permissions
// requires oscP5 libary installed in Processing SDK 3.0+

// Written by Andy Modla
// first revision 2016/10/26
// latest revision 2017/01/10
// use at you own risk with NXKS2 hack for Samsung NX500 cameras
// turn on Wifi in camera

    UdpClient client;
    //String BROADCAST = "255.255.255.255";
    String BROADCAST = "192.168.43.255";
    //String RIGHT = "192.168.43.203";
    //String RIGHT = "192.168.0.102";
    //String LEFT = "192.168.43.216";
    //String LEFT = "192.168.0.103";
    //ArrayList<NetAddress> ipList = new ArrayList(2);
    boolean broadcast = true;
    int port = 8000;
    Activity myActivity;
    SharedPreferences preferences;
    public static final String SETTINGS = "com.andymodla.remoteshutter";
    public static final String PHOTOINDEX = "photoIndex";
    public static final String VIDEOINDEX = "videoIndex";
    static final String CAMERA_MODE = "camera.mode";

    int black = color(0);   // black
    int gray = color(128);
    int white = color(255); // white
    int red = color(255, 0, 0);
    int aqua = color(128, 0, 128);
    int lightblue = color(64, 64, 128);
    int darkblue = color(32, 32, 64);
    int yellow = color(255, 204, 0);

    boolean focus = false;
    boolean focusHold = false;
    boolean shutter = false;
    boolean first_tap = false;
    boolean recording = false;
    boolean paused = false;
    boolean showMessage = false;
    String message = "";

    static final int PHOTO_MODE = 0;
    static final int VIDEO_MODE = 1;
    int mode = PHOTO_MODE;

    int counter = 0;
    int debounceCounter = 0;
    int messageCounter = 0;

    int BUTTON_SIZE = 300;
    int RECT_BUTTON_X = 0;
    int RECT_BUTTON_Y = 0;
    int CIRCLE_BUTTON_X = 0;
    int CIRCLE_BUTTON_Y = 0;

    int CHECKBOX_SIZE = 50;
    int PHOTO_CHECKBOX_X = 0;
    int PHOTO_CHECKBOX_Y = 0;
    int VIDEO_CHECKBOX_X = 0;
    int VIDEO_CHECKBOX_Y = 0;

    int DELAY = 30;
    int photoIndex = 0;  // next photo index for filename
    int videoIndex = 0;  // next video index for filename
    int FONT_SIZE = 48;
    int BASE_FONT_SIZE = 48;

    static final int MAIN_SCREEN = 0;
    static final int MENU_SCREEN = 1;
    int screen = MAIN_SCREEN;

    public void settings() {
        size(displayWidth, displayHeight, OPENGL);
        //fullScreen();  // causes navigation bar to be hidden
        orientation(LANDSCAPE);
        myActivity = getActivity();
        if (myActivity == null)
            println("activity null");
        preferences = myActivity.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE);
        photoIndex = preferences.getInt(PHOTOINDEX, 0);
        videoIndex = preferences.getInt(VIDEOINDEX, 0);
        mode = preferences.getInt(CAMERA_MODE, PHOTO_MODE);
        //ipList.clear();
        //ipList.add(0, new NetAddress(RIGHT, port));
        //ipList.add(1, new NetAddress(LEFT, port));
        //println("displayWidth="+displayWidth + " displayHeight="+displayHeight);
    }

    public void setup() {
        background(0);
        drawSetup();
        String wifiApIpAddress = getWifiApIpAddress();
        println("wifiapIpAddress=" + wifiApIpAddress);
        if (wifiApIpAddress == null) {
            BROADCAST = "255.255.255.255";
        } else {
            BROADCAST = wifiApIpAddress.substring(0, wifiApIpAddress.lastIndexOf('.')) + ".255";
        }
        println("new BROADCAST=" + BROADCAST);

        try {
            client = new UdpClient(BROADCAST, port);
            if (client == null) {
                println("WiFi Broadcast Failed "+BROADCAST + " " + port);
                showMessage("WiFi Broadcast Failed "+BROADCAST + " " + port);
                broadcast = false;
            } else if (client.socket() == null) {
                broadcast = false;
            }
        } catch (Exception e) {
            println("WiFi Broadcast Error "+BROADCAST + " " + port);
            broadcast = false;
            showMessage("WiFi Broadcast Error "+BROADCAST + " " + port);
        }
        broadcast = isBroadcastable("R");
//        if (!broadcast) {
//            if (client != null) {
//                DatagramSocket ds = client.socket();
//                if (ds != null) {
//                    ds.disconnect();
//                    ds.close();
//                }
//            }
//            broadcast = true;
//            client = new UdpClient(BROADCAST, port);
//            sendMsg("R");
//        }
    }

    private void drawSetup() {
        //println("width="+width + " height="+height);

        BUTTON_SIZE = width / 3;
        RECT_BUTTON_X = width / 8;
        RECT_BUTTON_Y = height / 5;
        CIRCLE_BUTTON_X = RECT_BUTTON_X + BUTTON_SIZE / 2 + width / 2;  // center
        CIRCLE_BUTTON_Y = RECT_BUTTON_Y + BUTTON_SIZE / 2 ;       // center
        CHECKBOX_SIZE = width / 15;
        PHOTO_CHECKBOX_X = width / 8;
        PHOTO_CHECKBOX_Y = height / 3;
        VIDEO_CHECKBOX_X = width / 2;
        VIDEO_CHECKBOX_Y = height / 3;
        FONT_SIZE = BASE_FONT_SIZE * (width / 800);

    }

    public void drawMain() {
        setTextSize(FONT_SIZE);
        // focus hold button
        if (focusHold)
            fill(lightblue);
        else
            fill(white);
        rect(RECT_BUTTON_X, RECT_BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE, 50);
        if (focusHold) {
            fill(darkblue);
            rect(RECT_BUTTON_X + height / 20, RECT_BUTTON_Y + height / 20, BUTTON_SIZE - height / 10, BUTTON_SIZE - height / 10, 25);
            fill(yellow);
        } else
            fill(0);
        if (mode == PHOTO_MODE) {
            text("FOCUS", RECT_BUTTON_X + BUTTON_SIZE / 6 + BUTTON_SIZE / 20, RECT_BUTTON_Y + BUTTON_SIZE / 2);
            text("HOLD", RECT_BUTTON_X + BUTTON_SIZE / 6 + BUTTON_SIZE / 20, RECT_BUTTON_Y + BUTTON_SIZE / 2 + BUTTON_SIZE / 8);
        } else // VIDEO_MODE
        {
            //fill(darkblue);
            //rect(width/8+height/20, height/5+height/20, BUTTON_SIZE-height/10, BUTTON_SIZE-height/10, 25);
            //fill(yellow);
            if (shutter && !paused)
                text("PAUSE", RECT_BUTTON_X + BUTTON_SIZE / 6, RECT_BUTTON_Y + BUTTON_SIZE / 2);
            else if (recording && paused)
                text("RESUME", RECT_BUTTON_X + BUTTON_SIZE / 6, RECT_BUTTON_Y + BUTTON_SIZE / 2);
            else
                text("OK", RECT_BUTTON_X + BUTTON_SIZE / 6, RECT_BUTTON_Y + BUTTON_SIZE / 2);
        }

        // shutter button
        if (shutter && focus)
            fill(red);
        else if (shutter)
            fill(red);
        else if (focus)
            fill(lightblue);
        else
            fill(white);
        ellipse(CIRCLE_BUTTON_X, CIRCLE_BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE);
        fill(aqua);
        ellipse(CIRCLE_BUTTON_X, CIRCLE_BUTTON_Y, BUTTON_SIZE / 8, BUTTON_SIZE / 8);
        if (focus)
            fill(yellow);
        else
            fill(black);
        if (mode == PHOTO_MODE) {
            text("SHUTTER", CIRCLE_BUTTON_X - BUTTON_SIZE/2 +  BUTTON_SIZE / 8 + BUTTON_SIZE / 20 , CIRCLE_BUTTON_Y - BUTTON_SIZE/2 +  BUTTON_SIZE / 4 + BUTTON_SIZE / 20);
        } else {
            if (!shutter)
                text("RECORD", CIRCLE_BUTTON_X - BUTTON_SIZE/2 +  BUTTON_SIZE / 8 + BUTTON_SIZE / 20 , CIRCLE_BUTTON_Y - BUTTON_SIZE/2 +  BUTTON_SIZE / 4 + BUTTON_SIZE / 20);
            else
                text("STOP", CIRCLE_BUTTON_X - BUTTON_SIZE/2 +  BUTTON_SIZE / 8 + BUTTON_SIZE / 20 , CIRCLE_BUTTON_Y - BUTTON_SIZE/2 +  BUTTON_SIZE / 4 + BUTTON_SIZE / 20);
        }
        if (mode == PHOTO_MODE) {
            fill(yellow);
            if (photoIndex != 0) {
                text(number(photoIndex), CIRCLE_BUTTON_X - BUTTON_SIZE/2 +  BUTTON_SIZE / 8 + BUTTON_SIZE / 20 , CIRCLE_BUTTON_Y  +  BUTTON_SIZE / 4 + BUTTON_SIZE / 20);
            }
        } else {
            fill(yellow);
            if (videoIndex != 0) {
                text(number(videoIndex), CIRCLE_BUTTON_X - BUTTON_SIZE/2 +  BUTTON_SIZE / 8 + BUTTON_SIZE / 20 , CIRCLE_BUTTON_Y  +  BUTTON_SIZE / 4 + BUTTON_SIZE / 20);
            }
        }
        if (showMessage && messageCounter > 0) {
            fill(red);
            text(message, width/20, (9*height)/10);
        }
    }

    public void drawMenu() {
        fill(white);
        int y = height / 11;
        setTextSize(FONT_SIZE / 2);
        text("Broadcast camera control messages sent to port 8000", width / 8, 3 * y);
        drawCheckbox();

        // finalally add credits
        setTextSize(FONT_SIZE / 2);
        fill(gray);
        text("Written by Andy Modla", width / 8, 7 * y);
        text("Copyright 2017 Tekla Inc", width / 8, 8 * y);
        text("All Rights Reserved", width / 8, 9 * y);
        text("Version " + BuildConfig.VERSION_NAME, width / 8, 10 * y);
    }

    public void drawCheckbox() {
        setTextSize(FONT_SIZE);
        if (mode == PHOTO_MODE) {
            stroke(red);
            strokeWeight(4);
            fill(white);
            rect(PHOTO_CHECKBOX_X, PHOTO_CHECKBOX_Y, CHECKBOX_SIZE, CHECKBOX_SIZE);
        } else {
            stroke(red);
            strokeWeight(4);
            fill(black);
            rect(PHOTO_CHECKBOX_X, PHOTO_CHECKBOX_Y, CHECKBOX_SIZE, CHECKBOX_SIZE);
        }
        fill(white);
        text("Photo", width / 4, height / 3 + height / 10);

        if (mode == VIDEO_MODE) {
            stroke(red);
            strokeWeight(4);
            fill(white);
            rect(VIDEO_CHECKBOX_X, VIDEO_CHECKBOX_Y, CHECKBOX_SIZE, CHECKBOX_SIZE);
        } else {
            stroke(red);
            strokeWeight(4);
            fill(black);
            rect(VIDEO_CHECKBOX_X, VIDEO_CHECKBOX_Y, CHECKBOX_SIZE, CHECKBOX_SIZE);
        }
        fill(white);
        text("Video", 2 * width / 4 + width / 8, height / 3 + height / 10);

        strokeWeight(1);
    }

    private void setTextSize(float size) {
        if (size <= 0)
            return;
        textSize(size);
    }

    public void draw() {
        background(0);
        drawSetup();
        setTextSize(FONT_SIZE);
        stroke(255);

        // common screen elements
        fill(gray);
        text("WIFI Remote Capture", RECT_BUTTON_X, height / 10);
        stroke(0);
        fill(lightblue);
        ellipse(width / 2 + width / 4 + width / 8, height / 14, BUTTON_SIZE / 10, BUTTON_SIZE / 10);
        ellipse(width / 2 + width / 4 + width / 8 + width / 24, height / 14, BUTTON_SIZE / 10, BUTTON_SIZE / 10);
        ellipse(width / 2 + width / 4 + width / 8 + width / 12, height / 14, BUTTON_SIZE / 10, BUTTON_SIZE / 10);

        switch (screen) {
            case MAIN_SCREEN:
                drawMain();
                break;
            case MENU_SCREEN:
                drawMenu();
                break;
        }

        // focus and shutter controls
        if (counter > 0) {
            counter--;
            if (counter == 0) {
                if (focus) {
                    focus = false;
                }
                if (shutter) {
                    shutter = false;
                }
            }
        }

        if (debounceCounter > 0)
            debounceCounter--;

        if (messageCounter > 0)
            messageCounter--;
    }

    public void onBackPressed() {
        println("onBackPressed  -----------------------");
        if (client != null) {
            DatagramSocket ds = client.socket();
            if (ds != null) {
                ds.close();
                ds.disconnect();
            }
        }
    }

    public void mousePressed() {
        if (screen == MAIN_SCREEN) {
            if (mouseY >= RECT_BUTTON_Y && mouseY <= (RECT_BUTTON_Y + BUTTON_SIZE)
                    && mouseX >= RECT_BUTTON_X && mouseX <= (RECT_BUTTON_X + BUTTON_SIZE)) {
                focus();
                return;
            }
            if (mouseY >= (CIRCLE_BUTTON_Y - BUTTON_SIZE / 2) && mouseY <= (CIRCLE_BUTTON_Y + BUTTON_SIZE / 2)
                    && mouseX >= (CIRCLE_BUTTON_X - BUTTON_SIZE / 2) && mouseX <= (CIRCLE_BUTTON_X + BUTTON_SIZE / 2)) {
                capture();
                return;
            }
        } else if (screen == MENU_SCREEN) {
            //println("mouseX="+mouseX + " mouseY="+mouseY);
            if (mouseX >= PHOTO_CHECKBOX_X && mouseX <= (PHOTO_CHECKBOX_X + CHECKBOX_SIZE) &&
                    mouseY >= PHOTO_CHECKBOX_Y && mouseY <= (PHOTO_CHECKBOX_Y + CHECKBOX_SIZE)) {
                modeChange(PHOTO_MODE);
                return;
            } else if (mouseX >= VIDEO_CHECKBOX_X && mouseX <= (VIDEO_CHECKBOX_X + CHECKBOX_SIZE) &&
                    mouseY >= VIDEO_CHECKBOX_Y && mouseY <= (VIDEO_CHECKBOX_Y + CHECKBOX_SIZE)) {
                modeChange(VIDEO_MODE);
                return;
            }
        }
        // common areas either screen
        if (mouseY < height / 10 && mouseX > width / 2 + width / 4 + width / 8) {
            //println("menu");
            // toggle screens
            if (screen == MAIN_SCREEN)
                screen = MENU_SCREEN;
            else
                screen = MAIN_SCREEN;
        }
    }

    public void modeChange(int change) {
        if (mode != change) {
            mode = change;
            SharedPreferences.Editor edit = preferences.edit();
            edit.putInt(CAMERA_MODE, mode);
            edit.commit();
            focus = false;
            focusHold = false;
            shutter = false;
            paused = false;
            if (recording) {
                recording = false;
                sendMsg("V");
            }
            sendMsg("R");  // reset
        }
    }

    public String number(int index) {
        // fix size of index number at 4 characters long
        if (index == 0)
            return "0000";
        else if (index < 10)
            return ("000" + String.valueOf(index));
        else if (index < 100)
            return ("00" + String.valueOf(index));
        else if (index < 1000)
            return ("0" + String.valueOf(index));
        return String.valueOf(index);
    }

    public void updatePhotoIndex() {
        photoIndex++;
        if (photoIndex > 9999) {
            photoIndex = 1;
        }
        SharedPreferences.Editor edit = preferences.edit();
        edit.putInt(PHOTOINDEX, photoIndex);
        edit.commit();
    }

    public void updateVideoIndex() {
        videoIndex++;
        if (videoIndex > 9999) {
            videoIndex = 1;
        }
        SharedPreferences.Editor edit = preferences.edit();
        edit.putInt(VIDEOINDEX, videoIndex);
        edit.commit();
    }

    public void capture() {
        if (mode == PHOTO_MODE) {
            // shutter button
            if (first_tap && !focusHold) {
                first_tap = false;
                shutter = true;
                updatePhotoIndex();
                sendMsg("C" + number(photoIndex));
                println("CAPTURE");
                counter = DELAY;
            } else {
                if (focusHold) {
                    shutter = true;
                    updatePhotoIndex();
                    sendMsg("S" + number(photoIndex));
                    println("SHUTTER");
                    counter = DELAY;
                } else {
                    first_tap = true;
                    focus = true;
                    sendMsg("F");
                    println("FOCUS");
                }
            }
        } else // VIDEO_MODE
        {
            if (!shutter) {
                shutter = true;
                updateVideoIndex();
                sendMsg("V" + number(videoIndex));
                println("RECORD");
                recording = true;
                paused = false;
                //counter = DELAY;
            } else {
                shutter = false;
                sendMsg("V");
                recording = false;
                paused = false;
                println("STOP");
            }
        }
    }

    public void focus() {
        if (mode == PHOTO_MODE) {
            // Focus hold toggle button
            focusHold = !focusHold;
            if (focusHold) {
                sendMsg("F");
                println("FOCUS HOLD");
            } else {
                sendMsg("R");
                focus = false;
                first_tap = false;
                println("FOCUS RELEASE");
            }
        } else // VIDEO_MODE
        {
            if (debounceCounter == 0) {
                sendMsg("P");
                paused = !paused;
                println("OK");
                debounceCounter = 6;
            }
        }
    }

    boolean isBroadcastable(String msg) {
        boolean status = false;
        try {
            DatagramSocket ds = client.socket();
            InetAddress var2 = InetAddress.getByName(BROADCAST);
            DatagramPacket dp = new DatagramPacket(msg.getBytes(), msg.length(), var2, port);
            ds.send(dp);
            status = true;
        } catch (IOException e) {
            println("failed " + BROADCAST);
        }
        println(BROADCAST + " broadcast net status " + status);
//        String wifiApIpAddress = getWifiApIpAddress();
//        println("wifiapIpAddress="+ wifiApIpAddress);
//        if (!status) {
//            BROADCAST = wifiApIpAddress.substring(0, wifiApIpAddress.lastIndexOf('.'))+".255";
//            println("new BROADCAST="+BROADCAST);
//        }
        return status;
    }

    public String getWifiApIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en
                    .hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                if (intf.getName().contains("wlan")) {
                    for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr
                            .hasMoreElements(); ) {
                        InetAddress inetAddress = enumIpAddr.nextElement();
                        //NetworkInterface networkInterface = NetworkInterface.getByInetAddress(inetAddress);
                        //for (InterfaceAddress address : networkInterface.getInterfaceAddresses()) {
                        //    println(address.getNetworkPrefixLength());
                        //}
                        //println("inetAddress="+inetAddress.getCanonicalHostName());
                        if (!inetAddress.isLoopbackAddress()
                                && (inetAddress.getAddress().length == 4)) {
                            //println("Host address="+inetAddress.getHostAddress());
                            return inetAddress.getHostAddress();
                        }
                    }
                }
            }
        } catch (SocketException ex) {
            println(ex.toString());
        }
        return null;
    }

    void sendMsg(String msg) {
        if (client != null) {
            if (broadcast) {
                client.send(msg);
                println("client.send msg=" + msg);
            } else {
                println("Network Not Reachable");
                showMessage("WiFi Network Not Reachable");
//                for (int i = 0; i < ipList.size(); i++) {
//                    client.send(msg.getBytes(), ipList.get(i));
//                    println("send " + msg + " to " + ipList.get(i).inetaddress().getHostAddress());
//                }
            }
        }
    }

    void showMessage(String text) {
        message = text;
        showMessage = true;
        messageCounter = 200;
    }

    // keyboard input for debug
    public void keyPressed() {
        //println(keyCode);
        //println("key="+key);
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || key == 's' || key == 'S' || key == 24) {
            capture();
        } else if (keyCode == KeyEvent.KEYCODE_ENTER || key == 'f' || key == 'F') {
            focus();
        }
    }

// NX500 commands
//else if (*buf == 'F') {
//  system("st key push s1"); // focus press hold
//}
//else if (*buf == 'C') {
//  system("st key push s2; st key release s2; st key release s1"); // shutter press and release all
//}
//else if (*buf == 'S') {
//  system("st key push s2; st key release s2"); // shutter press and release
//}
//else if (*buf == 'R') {
//  system("st key release s2; st key release s1"); // shutter release all
//}

    static public void main(String[] passedArgs) {
        String[] appletArgs = new String[]{"RemoteCapture"};
        if (passedArgs != null) {
            PApplet.main(concat(appletArgs, passedArgs));
        } else {
            PApplet.main(appletArgs);
        }
    }

}
