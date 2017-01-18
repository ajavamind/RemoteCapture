package com.andymodla.remotecapture;

import processing.core.*; 
import processing.data.*; 
import processing.event.*; 
import processing.opengl.*; 

import android.view.KeyEvent; 
import android.app.Activity; 
import android.content.Context; 
import android.content.SharedPreferences; 
import java.net.DatagramSocket; 
import netP5.*; 

import java.util.HashMap; 
import java.util.ArrayList; 
import java.io.File; 
import java.io.BufferedReader; 
import java.io.PrintWriter; 
import java.io.InputStream; 
import java.io.OutputStream; 
import java.io.IOException; 

public class RemoteCapture extends PApplet {

// Creates a broadcast client that sends focus/shutter key commands to NX500 cameras
// requires Internet Android permissions
// requires oscP5 libary installed in Processing SDK 3.0+

// Written by Andy Modla
// first revision 2016/10/26
// latest revision 2017/01/10
// use at you own risk with NXKS2 hack for Samsung NX500 cameras
// turn on Wifi in camera







// Processing libraries imports

//import oscP5.*; // does not use this part of oscP5 library

public static final String VERSION="1.0";

UdpClient client; 
String BROADCAST = "255.255.255.255";
//String BROADCAST = "255.0.255.255";
int port = 8000;
//PSurface pSurface;
Activity myActivity;
SharedPreferences preferences;
public static final String SETTINGS = "com.andymodla.remoteshutter";
public static final String PHOTOINDEX = "photoIndex";
static final String CAMERA_MODE= "cameraM.mode";

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

static final int PHOTO_MODE = 0;
static final int VIDEO_MODE = 1;
int mode = PHOTO_MODE;

int counter = 0;
int debounceCounter = 0;

int BUTTON_SIZE = 300;
int CHECKBOX_SIZE = 50;
int PHOTO_CHECKBOX_X = 0;
int PHOTO_CHECKBOX_Y = 0;
int VIDEO_CHECKBOX_X = 0;
int VIDEO_CHECKBOX_Y = 0;

int DELAY = 30;
int photoIndex = 0;  // next photo index for filename
int FONT_SIZE = 48;
int BASE_FONT_SIZE = 48;

static final int MAIN_SCREEN = 0;
static final int MENU_SCREEN = 1;
int screen = MAIN_SCREEN;

public void settings() {
  fullScreen();
  orientation(LANDSCAPE); 
  myActivity = getActivity();
  if (myActivity == null)
    println("activity null");
  preferences = myActivity.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE);
  photoIndex = preferences.getInt(PHOTOINDEX, 0);
  mode = preferences.getInt(CAMERA_MODE, PHOTO_MODE);
}

public void setup() { 
  background(0);
  BUTTON_SIZE = width/3;
  CHECKBOX_SIZE = width/15;
  PHOTO_CHECKBOX_X = width/8;
  PHOTO_CHECKBOX_Y = height/3;
  VIDEO_CHECKBOX_X = width/2;
  VIDEO_CHECKBOX_Y = height/3;
  FONT_SIZE = BASE_FONT_SIZE*(width/800);
  try {
    client = new UdpClient( BROADCAST, port);
    if (client == null) {
      println("Error UDP Client");
    }
  }
  catch (Exception e) {
    println("Wifi problem");
  }
} 

public void drawMain() {
  textSize(FONT_SIZE);
  // focus hold button
  if (focusHold)
    fill(lightblue);
  else
    fill(white);
  rect(width/8, height/5, BUTTON_SIZE, BUTTON_SIZE, 50);
  if (focusHold) {
    fill(darkblue);
    rect(width/8+height/20, height/5+height/20, BUTTON_SIZE-height/10, BUTTON_SIZE-height/10, 25);
    fill(yellow);
  } else
    fill(0);
  if (mode == PHOTO_MODE) {
    text("FOCUS", width/6 + width/20, height/2);
    text("HOLD", width/6 + width/20, height/2 + height/8);
  } else // VIDEO_MODE
  {
    //fill(darkblue);
    //rect(width/8+height/20, height/5+height/20, BUTTON_SIZE-height/10, BUTTON_SIZE-height/10, 25);
    //fill(yellow);
    if (shutter && !paused)
      text("PAUSE", width/6, height/2);
    else if (recording && paused)
      text("RESUME", width/6, height/2);
    else 
      text("OK", width/6, height/2);
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
  ellipse(width/2+width/4, height/2, BUTTON_SIZE, BUTTON_SIZE);
  fill(aqua);
  ellipse(width/2+width/4, height/2, BUTTON_SIZE/8, BUTTON_SIZE/8);
  if (focus)
    fill(yellow);
  else
    fill(black);
  if (mode == PHOTO_MODE) {
    text("SHUTTER", width/2+width/8 + width/20 - width/24, height/2+ height/8+ height/20);
  } else {
    if (!shutter)
      text("RECORD", width/2+width/8 + width/20- width/48, height/2+ height/8+ height/20);
    else
      text("STOP", width/2+width/8 + width/20, height/2+ height/8+ height/20);
  }
  if (mode == PHOTO_MODE) {
    fill(yellow);
    if (photoIndex !=0) {
      text(number(), (3*width)/4-width/20-width/100, (height*9)/10);
    }
  }
}

public void drawMenu() {
  fill(white);
  int y = height/11;
  textSize(FONT_SIZE/2);
  text("Broadcasting on port 8000", width/8, 3*y);
  drawCheckbox();

  // finalally add credits
  textSize(FONT_SIZE/2);
  fill(gray);
  text("Written by Andy Modla", width/8, 8*y);
  text("Copyright 2017 Tekla Inc", width/8, 9*y);
  text("All Rights Reserved", width/8, 10*y);
  text("Version "+VERSION, width/8, 11*y);
}

public void drawCheckbox() {
  textSize(FONT_SIZE);
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
  text("Photo", width/4, height/3+height/10);

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
  text("Video", 2*width/4+width/8, height/3+height/10);

  strokeWeight(1);
}

public void draw() { 
  background(0);
  textSize(FONT_SIZE);
  stroke(255);

  // common screen elements
  fill(gray);
  text("WIFI Remote Capture", width/4, height/10);
  stroke(0);
  fill(lightblue);
  ellipse(width/2+width/4+width/8, height/14, BUTTON_SIZE/10, BUTTON_SIZE/10);
  ellipse(width/2+width/4+width/8+width/24, height/14, BUTTON_SIZE/10, BUTTON_SIZE/10);
  ellipse(width/2+width/4+width/8+width/12, height/14, BUTTON_SIZE/10, BUTTON_SIZE/10);

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
} 

public void onBackPressed() {
  println("onBackPressed");
  if (client != null) {
    DatagramSocket ds = client.socket();
    if (ds!= null) {
      ds.disconnect();
      ds.close();
    }
  }
}

public void mousePressed() {
  if (screen == MAIN_SCREEN) {
    if (mouseY > height/6 && mouseY < (5*height)/6) {
      if (mouseX > width/2) {
        capture();
      } else {
        focus();
      }
      return;
    }
  } else if (screen == MENU_SCREEN) {
    //println("mouseX="+mouseX + " mouseY="+mouseY);
    if (mouseX >= PHOTO_CHECKBOX_X && mouseX <= (PHOTO_CHECKBOX_X+CHECKBOX_SIZE) &&
      mouseY >= PHOTO_CHECKBOX_Y && mouseY <= (PHOTO_CHECKBOX_Y+CHECKBOX_SIZE)) {
      modeChange(PHOTO_MODE);
      return;
    } else if (mouseX >= VIDEO_CHECKBOX_X && mouseX <= (VIDEO_CHECKBOX_X+CHECKBOX_SIZE) &&
      mouseY >= VIDEO_CHECKBOX_Y && mouseY <= (VIDEO_CHECKBOX_Y+CHECKBOX_SIZE)) {
      modeChange(VIDEO_MODE);
      return;
    }
  }
  // common areas either screen
  if (mouseY <  height/10 && mouseX > width/2+width/4+width/8) {
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
      client.send("V");
    }
    client.send("R");  // reset
  }
}

public String number() {
  // TODO fix size at 4 characters long
  if (photoIndex == 0)
    return "0000";
  else if (photoIndex < 10)
    return ("000" + String.valueOf(photoIndex));
  else if (photoIndex < 100)
    return ("00" + String.valueOf(photoIndex));
  else if (photoIndex < 1000)
    return ("0" + String.valueOf(photoIndex));
  return String.valueOf(photoIndex);
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

public void capture() {
  if (mode == PHOTO_MODE) {
    // shutter button
    if (first_tap && !focusHold) {
      first_tap = false;
      shutter = true;
      updatePhotoIndex();
      client.send("C"+number());
      println("CAPTURE");
      counter = DELAY;
    } else {
      if (focusHold) {
        shutter = true;
        updatePhotoIndex();
        client.send("S"+number());
        println("SHUTTER");
        counter = DELAY;
      } else {
        first_tap = true;
        focus = true;
        client.send("F");
        println("FOCUS");
      }
    }
  } else // VIDEO_MODE
  {
    if (!shutter) {
      shutter = true;
      client.send("V");
      println("RECORD");
      recording = true;
      paused = false;
      //counter = DELAY;
    } else {
      shutter = false;
      client.send("V");
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
      client.send("F");
      println("FOCUS HOLD");
    } else {
      client.send("R");
      focus = false;
      first_tap = false;
      println("FOCUS RELEASE");
    }
  } else // VIDEO_MODE
  {
    if (debounceCounter == 0) {
    client.send("P");
    paused = !paused;
    println("OK");
    debounceCounter = 6;
    }
  }
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
    String[] appletArgs = new String[] { "RemoteCapture" };
    if (passedArgs != null) {
      PApplet.main(concat(appletArgs, passedArgs));
    } else {
      PApplet.main(appletArgs);
    }
  }
}
