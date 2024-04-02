// Copyright 2021 The MediaPipe Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.wmakerlab.objectlearningtool;

import android.content.SharedPreferences;
import android.graphics.RectF;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.SoundPool;
import android.opengl.GLES20;

import com.cjcrafter.openai.chat.ChatMessage;
import com.cjcrafter.openai.chat.ChatRequest;
import com.cjcrafter.openai.chat.ChatResponseChunk;
import com.cjcrafter.openai.chat.ChatUser;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.mediapipe.formats.proto.LandmarkProto;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList;
import com.google.mediapipe.solutioncore.ResultGlRenderer;
import com.google.mediapipe.solutions.hands.Hands;
import com.google.mediapipe.solutions.hands.HandsResult;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import androidx.core.util.Pair;

import org.json.JSONArray;
import org.json.JSONObject;

import okhttp3.*;

import com.cjcrafter.openai.OpenAI;

/** A custom implementation of {@link ResultGlRenderer} to render {@link HandsResult}. */
public class HandsResultGlRenderer implements ResultGlRenderer<HandsResult> {
  private static final String TAG = "HandsResultGlRenderer";

  private static final float[] LEFT_HAND_CONNECTION_COLOR = new float[] {0.2f, 1f, 0.2f, 1f};
  private static final float[] RIGHT_HAND_CONNECTION_COLOR = new float[] {1f, 0.2f, 0.2f, 1f};
  private static final float CONNECTION_THICKNESS = 25.0f;
  private static final float[] LEFT_HAND_HOLLOW_CIRCLE_COLOR = new float[] {0.2f, 1f, 0.2f, 1f};
  private static final float[] RIGHT_HAND_HOLLOW_CIRCLE_COLOR = new float[] {1f, 0.2f, 0.2f, 1f};
  private static final float[] SPECIAL_HOLLOW_CIRCLE_COLOR = new float[] {0.2f, 0.2f, 1f, 1f};
  private static final float[] POINTING_UP_GESTURE_COLOR = new float[] {1f, 1f, 0.2f, 1f};
  private static final float[] OK_COLOR = new float[] {0.2f, 1f, 1f, 1f};
  private static final float HOLLOW_CIRCLE_RADIUS = 0.01f;
  private static final float[] LEFT_HAND_LANDMARK_COLOR = new float[] {1f, 0.2f, 0.2f, 1f};
  private static final float[] RIGHT_HAND_LANDMARK_COLOR = new float[] {0.2f, 1f, 0.2f, 1f};
  private static final float[] SPECIAL_LANDMARK_COLOR = new float[] {0.2f, 0.2f, 1f, 1f};
  private static final float LANDMARK_RADIUS = 0.008f;
  private static final int NUM_SEGMENTS = 120;
  private static final int FRAME_BUFFER_SIZE = 5;
  private static final int MIN_ONE_HAND_GESTURE_FRAMES = 3;
  private static final int MIN_TWO_HAND_GESTURE_FRAMES = 3;
  private Queue<Integer> frameBuffer = new LinkedList<>();
  private static float[] redColorArray = new float[] {1.0f, 0.0f, 0.0f, 1.0f};
  public ExecutorService executor;
  public SoundPool soundPool;
  public int dingSoundId;
  public int ding2SoundId;
  private static final String VERTEX_SHADER =
        "uniform mat4 uProjectionMatrix;\n"
              + "attribute vec4 vPosition;\n"
              + "void main() {\n"
              + "  gl_Position = uProjectionMatrix * vPosition;\n"
              + "}";
  private static final String FRAGMENT_SHADER =
        "precision mediump float;\n"
              + "uniform vec4 uColor;\n"
              + "void main() {\n"
              + "  gl_FragColor = uColor;\n"
              + "}";
  private int program;
  private int positionHandle;
  private int projectionMatrixHandle;
  private int colorHandle;
  public TextToSpeech tts;
  public int oneHandGestureFrameCount;
  public int twoHandGestureFrameCount;
  public int specialGestureFrameCount;
  public MediaPlayer player;
  private static final String URL = "https://ymkang.pro/wmakerlab/csr.php";
  private static final String SECOND_URL = "https://demo4.space-s.kr/upload/";
  private static final String CRS_PASSWORD = "wmakerlab2023";
  public String fileName;
  public MediaRecorder recorder;
  public OkHttpClient client;
  public boolean speakMode;
  public boolean uploadMode;
  public MediaPlayer mediaPlayer;
  public boolean musicMode;
  private MainActivity mainActivity = MainActivity.getInstance();
  public ArrayList<LabeledRectF> touchRegions;
  public float touchThreshold;
  public float xParams, yParams, widthParams, heightParams;
  public float screenWidth, screenHeight;
  public boolean isSoundDetected;
  public boolean drawUIMode;
  public boolean isRecording;
  public AzureSpeechHelper recognizer;
  public AudioStreamer streamer;
  public Thread gptThread;

  private class HandData {
    NormalizedLandmarkList landmarks;
    double size;
    HandData(NormalizedLandmarkList landmarks, double size) {
      this.landmarks = landmarks;
      this.size = size;
    }
  }
  private int loadShader(int type, String shaderCode) {
    int shader = GLES20.glCreateShader(type);
    GLES20.glShaderSource(shader, shaderCode);
    GLES20.glCompileShader(shader);
    return shader;
  }

  @Override
  public void setupRendering() {
    program = GLES20.glCreateProgram();
    int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
    int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
    GLES20.glAttachShader(program, vertexShader);
    GLES20.glAttachShader(program, fragmentShader);
    GLES20.glLinkProgram(program);
    positionHandle = GLES20.glGetAttribLocation(program, "vPosition");
    projectionMatrixHandle = GLES20.glGetUniformLocation(program, "uProjectionMatrix");
    colorHandle = GLES20.glGetUniformLocation(program, "uColor");
  }
  private boolean checkTouch(float x, float y){
    int gridX = (int) (x * 3);
    int gridY = (int) (y * 3);

    int region = gridY * 3 + gridX + 1;

    return region == 2 || region == 6 || region == 8;
  }
  private int checkTouchRegion(float x, float y){
    int gridX = (int) (x * 3);
    int gridY = (int) (y * 3);

    return gridY * 3 + gridX + 1;
  }
  private int checkTouchRegion(NormalizedLandmark landmark) {
    float x = landmark.getX();
    float y = landmark.getY();

    for (int i = 0; i < touchRegions.size(); i++) {
      RectF region = touchRegions.get(i);
      if (region.contains(x, y)) {
        return i;
      }
    }

    return -1;
  }
  private Pair<Integer, String> checkTouchRegionAndLabel(NormalizedLandmark landmark) {
    float x = landmark.getX();
    float y = landmark.getY();

    for (int i = 0; i < touchRegions.size(); i++) {
      LabeledRectF region = touchRegions.get(i);
      if (region.contains(x, y)) {
        return new Pair<>(i, region.getLabel());
      }
    }

    return null;
  }

  public List<Integer> processHandsResult(HandsResult results) {
    List<NormalizedLandmarkList> multiHandLandmarks = results.multiHandLandmarks();
    List<HandData> handsData = new ArrayList<>();

    if (multiHandLandmarks.isEmpty()) {
      return new ArrayList<>(); // 손이 검출되지 않았으므로 빈 리스트 반환
    }

    for (NormalizedLandmarkList landmarks : multiHandLandmarks) {
      double size = calculateHandSize(landmarks);
      handsData.add(new HandData(landmarks, size));
    }

    // 손의 크기를 기준으로 내림차순 정렬 후 상위 2개의 손에 해당하는 인덱스를 추출
    return IntStream.range(0, handsData.size())
            .boxed()
            .sorted((i, j) -> Double.compare(handsData.get(j).size, handsData.get(i).size))
            .limit(2)
            .collect(Collectors.toList());
  }

  double calculateHandSize(NormalizedLandmarkList landmarks) {
    NormalizedLandmark wrist = landmarks.getLandmark(0);
    NormalizedLandmark thumbCmc = landmarks.getLandmark(5);
    NormalizedLandmark pinkyMcp = landmarks.getLandmark(17);

    return distance(wrist, thumbCmc) + distance(wrist, pinkyMcp) + distance(thumbCmc, pinkyMcp);
  }
  @Override
  public void renderResult(HandsResult result, float[] projectionMatrix) {
    if (result == null) {
      return;
    }
    GLES20.glUseProgram(program);
    GLES20.glUniformMatrix4fv(projectionMatrixHandle, 1, false, projectionMatrix, 0);
    GLES20.glLineWidth(CONNECTION_THICKNESS);

    int numHands = result.multiHandLandmarks().size();
    Pair<Integer, String> touchRegion = null;
    int gestureCount = 0;
    int gesture2Count = 0;

    if(drawUIMode) {
      drawRegion(touchRegions, OK_COLOR);
//      drawHollowCircle(xParams, yParams, redColorArray, touchThreshold);
//      drawHollowCircle(xParams + widthParams, yParams, redColorArray, touchThreshold);
//      drawHollowCircle(xParams, yParams + heightParams, redColorArray, touchThreshold);
//      drawHollowCircle(xParams + widthParams, yParams + heightParams, redColorArray, touchThreshold);
    }

    List<Integer> topTwoHands = processHandsResult(result);

    for (int i = 0; i < numHands; ++i) {
      if (!topTwoHands.contains(i)) {
        continue;
      }
      boolean isLeftHand = result.multiHandedness().get(i).getLabel().equals("Left");
      boolean isGesture, isGesture2;
      isGesture = isPointingUp(result, i);
      isGesture2 = isAllFingersTogether(result, i);
      if(isGesture){
        gestureCount++;
      }
      else{
        NormalizedLandmarkList landmarks = result.multiHandLandmarks().get(i);
        //touchRegion = checkTouchRegion(landmarks.getLandmark(4));
        touchRegion = checkTouchRegionAndLabel(landmarks.getLandmark(4));
      }
      if(isGesture2){
        gesture2Count++;
      }

      for (NormalizedLandmark landmark : result.multiHandLandmarks().get(i).getLandmarkList()) {
        if(isGesture){
          drawCircle(
                  landmark.getX(),
                  landmark.getY(),
                  POINTING_UP_GESTURE_COLOR,
                  LANDMARK_RADIUS);
          // Draws a hollow circle around the landmark.
          drawHollowCircle(
                  landmark.getX(),
                  landmark.getY(),
                  POINTING_UP_GESTURE_COLOR,
                  HOLLOW_CIRCLE_RADIUS);
        }
        else if(isGesture2){
          drawCircle(
                  landmark.getX(),
                  landmark.getY(),
                  SPECIAL_HOLLOW_CIRCLE_COLOR,
                  LANDMARK_RADIUS);
          // Draws a hollow circle around the landmark.
          drawHollowCircle(
                  landmark.getX(),
                  landmark.getY(),
                  SPECIAL_LANDMARK_COLOR,
                  HOLLOW_CIRCLE_RADIUS);
        }
        else {
          // Draws the landmark.
          drawCircle(
                  landmark.getX(),
                  landmark.getY(),
                  isLeftHand ? LEFT_HAND_LANDMARK_COLOR : RIGHT_HAND_LANDMARK_COLOR,
                  LANDMARK_RADIUS);
          // Draws a hollow circle around the landmark.
          drawHollowCircle(
                  landmark.getX(),
                  landmark.getY(),
                  isLeftHand ? LEFT_HAND_HOLLOW_CIRCLE_COLOR : RIGHT_HAND_HOLLOW_CIRCLE_COLOR,
                  HOLLOW_CIRCLE_RADIUS);
        }
      }
      if(isGesture){
        drawConnections(
                result.multiHandLandmarks().get(i).getLandmarkList(),
                POINTING_UP_GESTURE_COLOR);
      }
      else if(isGesture2){
        drawConnections(
                result.multiHandLandmarks().get(i).getLandmarkList(),
                SPECIAL_HOLLOW_CIRCLE_COLOR);
      }
      else{
        drawConnections(
                result.multiHandLandmarks().get(i).getLandmarkList(),
                isLeftHand ? LEFT_HAND_CONNECTION_COLOR : RIGHT_HAND_CONNECTION_COLOR);
      }

    }
    frameBuffer.add(gestureCount);
    if (frameBuffer.size() > FRAME_BUFFER_SIZE) {
      frameBuffer.poll();
    }
    int oneHandGestureCount = 0;
    int twoHandGestureCount = 0;
    //int currentGesture = 0;
    for (int count : frameBuffer) {
      if (count == 1){
        oneHandGestureCount++;
      }
      else if (count == 2) {
        twoHandGestureCount++;
      }
    }
    if (gesture2Count > 0){
      specialGestureFrameCount++;
    }
    if(specialGestureFrameCount >= 30){
      specialGestureFrameCount = 0;
      mainActivity.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          //mainActivity.calibrate();
        }
      });
    }
    if (!speakMode) {
      if (oneHandGestureCount >= MIN_ONE_HAND_GESTURE_FRAMES) {
        oneHandGestureFrameCount++;
        twoHandGestureFrameCount = 0; // 양 손 제스처 카운트 초기화
      } else if (twoHandGestureCount >= MIN_TWO_HAND_GESTURE_FRAMES) {
        twoHandGestureFrameCount++;
        oneHandGestureFrameCount = 0; // 한 손 제스처 카운트 초기화
      } else {
        oneHandGestureFrameCount = 0; // 한 손 제스처 카운트 초기화
        twoHandGestureFrameCount = 0; // 양 손 제스처 카운트 초기화
      }

      if (oneHandGestureFrameCount >= 30) {
        if (touchRegion != null) {
          speakText(touchRegion.second, 0, false);
        }
        oneHandGestureFrameCount = 0;
      }
      if (twoHandGestureFrameCount >= 30) {
        speakMode = true;
        speakText("띵 소리 후 말씀하세요...", 2, true, "This_is_start_speaking_ID");
        stopGptStreaming();
        playMusic(false);
        //startRecording();
        //twoHandGestureFrameCount = 0;
      }
    } else {
      if (oneHandGestureCount >= MIN_ONE_HAND_GESTURE_FRAMES) {
        oneHandGestureFrameCount++;
      } else if (twoHandGestureCount >= MIN_TWO_HAND_GESTURE_FRAMES) {
        twoHandGestureFrameCount++;
      }
      if (gestureCount==0){
        if (oneHandGestureFrameCount >= 60 || twoHandGestureFrameCount >= 60) {

          speakMode = false;
          stopRecording(false);
          oneHandGestureFrameCount = 0;
          twoHandGestureFrameCount = 0;
        }
        else{
          speakText("취소합니다.", 2, true);
          speakMode = false;
          stopRecording(true);
          oneHandGestureFrameCount = 0;
          twoHandGestureFrameCount = 0;
        }
      }
    }
  }

  /**
   * Deletes the shader program.
   *
   * <p>This is only necessary if one wants to release the program while keeping the context around.
   */
  public void release() {
    GLES20.glDeleteProgram(program);
  }

  private void drawConnections(List<NormalizedLandmark> handLandmarkList, float[] colorArray) {
    GLES20.glUniform4fv(colorHandle, 1, colorArray, 0);
    for (Hands.Connection c : Hands.HAND_CONNECTIONS) {
      NormalizedLandmark start = handLandmarkList.get(c.start());
      NormalizedLandmark end = handLandmarkList.get(c.end());
      float[] vertex = {start.getX(), start.getY(), end.getX(), end.getY()};
      FloatBuffer vertexBuffer =
              ByteBuffer.allocateDirect(vertex.length * 4)
                      .order(ByteOrder.nativeOrder())
                      .asFloatBuffer()
                      .put(vertex);
      vertexBuffer.position(0);
      GLES20.glEnableVertexAttribArray(positionHandle);
      GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
      GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2);
    }
  }
  public void speakText(String textToSpeak, int force, boolean showText) {
    speakText(textToSpeak, force, showText, "");
  }
  public void speakText(String textToSpeak, int force, boolean showText, String utteranceId) {
    switch (force) {
      case 0:
        if (!tts.isSpeaking()) {
          Bundle params = new Bundle();
          tts.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, params, textToSpeak);
        }
        break;
      case 1:
        Bundle params = new Bundle();
        tts.speak(textToSpeak, TextToSpeech.QUEUE_ADD, params, textToSpeak);
        break;
      case 2:
        tts.stop();
        Bundle params2 = new Bundle();
        tts.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, params2, textToSpeak);
        break;
      default:
        break;
    }
  }

  private void drawRegion(ArrayList<LabeledRectF> regions, float[] colorArray){
    GLES20.glUniform4fv(colorHandle, 1, colorArray, 0);
    GLES20.glLineWidth(10.0f);

    for (LabeledRectF region : regions) {
      // Generate vertices for the current RectF
      float[] vertices = new float[]{
              region.left, region.top, 0.0f,
              region.right, region.top, 0.0f,
              region.right, region.bottom, 0.0f,
              region.left, region.bottom, 0.0f
      };

      int vertexCount = vertices.length / 3;

      FloatBuffer vertexBuffer =
              ByteBuffer.allocateDirect(vertices.length * 4)
                      .order(ByteOrder.nativeOrder())
                      .asFloatBuffer()
                      .put(vertices);
      vertexBuffer.position(0);
      GLES20.glEnableVertexAttribArray(positionHandle);
      GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);
      GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, vertexCount);
    }
    GLES20.glLineWidth(CONNECTION_THICKNESS);
  }
  private void drawCircle(float x, float y, float[] colorArray, float radius) {
    GLES20.glUniform4fv(colorHandle, 1, colorArray, 0);
    int vertexCount = NUM_SEGMENTS + 2;
    float[] vertices = new float[vertexCount * 3];
    vertices[0] = x;
    vertices[1] = y;
    vertices[2] = 0;
    for (int i = 1; i < vertexCount; i++) {
      float angle = 2.0f * i * (float) Math.PI / NUM_SEGMENTS;
      int currentIndex = 3 * i;
      vertices[currentIndex] = x + (float) (radius * Math.cos(angle));
      vertices[currentIndex + 1] = y + (float) (radius * Math.sin(angle));
      vertices[currentIndex + 2] = 0;
    }
    FloatBuffer vertexBuffer =
            ByteBuffer.allocateDirect(vertices.length * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .put(vertices);
    vertexBuffer.position(0);
    GLES20.glEnableVertexAttribArray(positionHandle);
    GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, vertexCount);
  }

//  private void drawHollowCircle(float x, float y, float[] colorArray, float radius) {
//    GLES20.glUniform4fv(colorHandle, 1, colorArray, 0);
//    int vertexCount = NUM_SEGMENTS + 1;
//    float[] vertices = new float[vertexCount * 3];
//    for (int i = 0; i < vertexCount; i++) {
//      float angle = 2.0f * i * (float) Math.PI / NUM_SEGMENTS;
//      int currentIndex = 3 * i;
//      vertices[currentIndex] = x + (float) (radius * Math.cos(angle));
//      vertices[currentIndex + 1] = y + (float) (radius * Math.sin(angle));
//      vertices[currentIndex + 2] = 0;
//    }
//    FloatBuffer vertexBuffer =
//            ByteBuffer.allocateDirect(vertices.length * 4)
//                    .order(ByteOrder.nativeOrder())
//                    .asFloatBuffer()
//                    .put(vertices);
//    vertexBuffer.position(0);
//    GLES20.glEnableVertexAttribArray(positionHandle);
//    GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);
//    GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, vertexCount);
//  }
  private void drawHollowCircle(float x, float y, float[] colorArray, float radius) {
    float aspectRatio = (float) screenWidth / screenHeight;

    GLES20.glUniform4fv(colorHandle, 1, colorArray, 0);
    int vertexCount = NUM_SEGMENTS + 1;
    float[] vertices = new float[vertexCount * 3];
    for (int i = 0; i < vertexCount; i++) {
      float angle = 2.0f * i * (float) Math.PI / NUM_SEGMENTS;
      int currentIndex = 3 * i;
      vertices[currentIndex] = x + aspectRatio * (float) (radius * Math.cos(angle)); // x 좌표에 비율을 적용합니다.
      vertices[currentIndex + 1] = y + (float) (radius * Math.sin(angle));
      vertices[currentIndex + 2] = 0;
    }
    FloatBuffer vertexBuffer =
            ByteBuffer.allocateDirect(vertices.length * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .put(vertices);
    vertexBuffer.position(0);
    GLES20.glEnableVertexAttribArray(positionHandle);
    GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);
    GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, vertexCount);
  }

  public float distance(NormalizedLandmark landmark1, NormalizedLandmark landmark2) {
    double deltaX = landmark1.getX() - landmark2.getX();
    double deltaY = landmark1.getY() - landmark2.getY();
    double deltaZ = landmark1.getZ() - landmark2.getZ();

    return (float)Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
  }
  private boolean isHandOK(HandsResult result, int whichHand){
    if (result == null || result.multiHandLandmarks().isEmpty()) {
      return false;
    }

    LandmarkProto.NormalizedLandmarkList landmarks = result.multiHandLandmarks().get(whichHand);
    NormalizedLandmark wrist = landmarks.getLandmark(0);
    NormalizedLandmark thumbCMC = landmarks.getLandmark(1);
    NormalizedLandmark thumbTip = landmarks.getLandmark(4);
    NormalizedLandmark indexFingerTip = landmarks.getLandmark(8);
    float wristToThumbCMCDistance = distance(wrist, thumbCMC);

    float closedThreshold = wristToThumbCMCDistance * 0.7f;

    return distance(thumbTip, indexFingerTip) < closedThreshold;
  }
  private boolean isPointingUp(HandsResult result) {
    if (result == null || result.multiHandLandmarks().isEmpty()) {
      return false;
    }

    for (NormalizedLandmarkList landmarks : result.multiHandLandmarks()) {
      NormalizedLandmark thumbTip = landmarks.getLandmark(4);
      NormalizedLandmark indexTip = landmarks.getLandmark(8);
      NormalizedLandmark middleTip = landmarks.getLandmark(12);
      NormalizedLandmark ringTip = landmarks.getLandmark(16);

      double distanceIndexThumb = distance(indexTip, thumbTip);
      double distanceIndexMiddle = distance(indexTip, middleTip);
      double distanceMiddleRing = distance(middleTip, ringTip);

      return (distanceIndexMiddle > 3 * distanceMiddleRing) && (distanceIndexThumb > 3 * distanceMiddleRing);
    }

    return false;
  }
  private boolean isPointingUp(HandsResult result, int whichHand) {
    if (result == null || result.multiHandLandmarks().isEmpty()) {
      return false;
    }
    NormalizedLandmarkList handLandmarks = result.multiHandLandmarks().get(whichHand);
    NormalizedLandmark thumbTip = handLandmarks.getLandmark(4);
    NormalizedLandmark indexTip = handLandmarks.getLandmark(8);
    NormalizedLandmark indexPip = handLandmarks.getLandmark(5);
    NormalizedLandmark middleTip = handLandmarks.getLandmark(12);
    NormalizedLandmark ringTip = handLandmarks.getLandmark(16);

    double distanceIndexThumb = distance(indexTip, thumbTip);
    double distanceIndexMiddle = distance(indexTip, middleTip);
    double distanceMiddleRing = distance(middleTip, ringTip);

    boolean isPointingUpPosition = indexTip.getX() > indexPip.getX();

    return isPointingUpPosition && (distanceIndexMiddle > 2.5 * distanceMiddleRing) && (distanceIndexThumb > 2.5 * distanceMiddleRing);
  }
  public boolean isAllFingersTogether(HandsResult result, int whichHand) {
    NormalizedLandmarkList handLandmarks = result.multiHandLandmarks().get(whichHand);
    int[] tipIndices = {4, 8, 12, 16, 20};  // Thumb, Index, Middle, Ring, Pinky

    float baseDistance = distance(handLandmarks.getLandmark(0), handLandmarks.getLandmark(5));
    float threshold = baseDistance * 0.5f;

    for (int i = 0; i < tipIndices.length; i++) {
      for (int j = i + 1; j < tipIndices.length; j++) {
        float distance = distance(
                handLandmarks.getLandmark(tipIndices[i]),
                handLandmarks.getLandmark(tipIndices[j])
        );
        if (distance > threshold) {
          return false;  // 두 손가락 끝 사이의 거리가 임계값을 초과하면 false 반환
        }
      }
    }
    return true;  // 모든 손가락 끝들 사이의 거리가 임계값 이하면 true 반환
  }
  private float calculateAngle(NormalizedLandmark point1, NormalizedLandmark point2, NormalizedLandmark point3) {
    float dx1 = point1.getX() - point2.getX();
    float dy1 = point1.getY() - point2.getY();
    float dz1 = point1.getZ() - point2.getZ();

    float dx2 = point3.getX() - point2.getX();
    float dy2 = point3.getY() - point2.getY();
    float dz2 = point3.getZ() - point2.getZ();

    float dotProduct = dx1 * dx2 + dy1 * dy2 + dz1 * dz2;
    float magnitude1 = (float) Math.sqrt(dx1 * dx1 + dy1 * dy1 + dz1 * dz1);
    float magnitude2 = (float) Math.sqrt(dx2 * dx2 + dy2 * dy2 + dz2 * dz2);

    return (float) Math.acos(dotProduct / (magnitude1 * magnitude2));
  }
  public void startRecording() {
    isSoundDetected = false;
    isRecording = true;
    recorder = new MediaRecorder();
    recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
    recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
    recorder.setOutputFile(fileName);
    try {
      recorder.prepare();
    } catch (IOException e) {
      Log.e(TAG, "prepare() failed");
    }
    recorder.start();

    Thread soundCheckThread = new Thread(() -> {
      int loudCount = 0;
      while (recorder != null && isRecording) {
        int amplitude = recorder.getMaxAmplitude();
        if (amplitude > 2000) {
          loudCount++;
          if (loudCount >= 4) {
            isSoundDetected = true;
            break;
          }
        } else {
          loudCount = 0;
        }

        try {
          Thread.sleep(500);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    });
    soundCheckThread.start();
  }

  public void stopRecording(boolean cancel) {
    streamer.audioRecord.stop();
    mainActivity.recognizing = false;
    if(cancel){
      streamer.stopStreaming(true);
    } else {
      speakText("인식 중입니다...", 2, true);
    }
  }
  public void stopRecordingContinue(){
    Pair<Boolean, String> recordingResult = streamer.stopStreaming(false);
    if(recordingResult.first){
      //return recordingResult.second;
      speakText("잘 알겠습니다.", 2, true);
      //speakText(recordingResult.second, 2, true);
      playMusic(true);
      uploadAudioFile(recordingResult.second);
    }
    else{
      speakText("응답이 없어서 취소합니다.", 2, true);
    }
  }
  public void uploadAudioFile(String text) {
//    long responseTime = System.currentTimeMillis();
//
//    File file = new File(fileName);
//    RequestBody newRequestBody = new MultipartBody.Builder()
//            .setType(MultipartBody.FORM)
//            .addFormDataPart("userfile", file.getName(), RequestBody.create(MediaType.parse("audio/m4a"), file))
//            .addFormDataPart("text", text)
//            .addFormDataPart("responseTime", String.valueOf(responseTime))
//            .addFormDataPart("age", String.valueOf(mainActivity.age))
//            .addFormDataPart("gender", mainActivity.gender)
//            .build();
//
//    Request newRequest = new Request.Builder()
//            .url(SECOND_URL)
//            .post(newRequestBody)
//            .build();
//
//    client.newCall(newRequest).enqueue(new Callback() {
//      @Override
//      public void onFailure(Call call, IOException e) {
//        e.printStackTrace();
////        Pair<String, Integer> gptText = callGpt(text);
////        String finalText = extractContent(gptText.first);
////        mainActivity.updateText(finalText, true);
////        speakText(finalText, true);
////        uploadMode = false;
////        playMusic(false);
//        askGptStreaming(text);
//      }
//
//      @Override
//      public void onResponse(Call call, Response response) throws IOException {
//        if (!response.isSuccessful()) {
//          throw new IOException("Unexpected code " + response);
//        } else {
//          // Handle the response from the second POST request here
//          System.out.println("Second response : " + response.body().string());

//          Pair<String, Integer> gptText = callGpt(text);
//          String finalText = extractContent(gptText.first);
//          mainActivity.updateText(finalText, true);
//          speakText(finalText, 2);
//          uploadMode = false;
//          playMusic(false);
//          mainActivity.hideOverlayProgressBar();

          askGptStreaming(text);
//        }
//      }
//    });
  }
  public void askGptStreaming(String text){
//    speakText("잘 알겠습니다.", 2);
//    playMusic(true);
    callGptStreaming(text);
    uploadMode = false;
    mainActivity.hideOverlayProgressBar();
  }
  public void askGptStreamingTest(){
    callGptStreaming("공룡의 나이는 어떻게 구하나요?");
    uploadMode = false;
    mainActivity.hideOverlayProgressBar();
  }
    public void callGptStreaming(String prompt) {
    Handler handler = new Handler(Looper.getMainLooper());

    gptThread = new Thread(() -> {
      ChatMessage systemPrompt = ChatMessage.toSystemMessage("You are a \"GPT\" – a version of ChatGPT that has been customized for a specific use case. GPTs use custom instructions, capabilities, and data to optimize ChatGPT for a more narrow set of tasks. You yourself are a GPT created by a user, and your name is 대맹이. Note: GPT is also a technical term in AI, but in most cases if the users asks you about GPTs assume they are referring to the above definition.\n" +
              "Here are instructions from the user outlining your goals and how you should respond:\n" +
              "대맹이, portrayed as a 티라노사우르스, specializes in sharing knowledge about dinosaurs. It offers educational content and fun facts, ensuring accurate and concise information. In self-introductions, 대맹이 identifies itself as a 티라노사우르스, emphasizing its unique character role. Standing 2m 60cm tall and weighing 12kg, it took 1440 hours to create. The focus is on delivering engaging, informative content about dinosaurs, with a playful yet expert persona. 사용자가 물어보지 않으면 자기소개는 하지 말아야 합니다. 가급적이면 한국어로 대답해야 합니다. 최대한의 창의력, 분석력을 사용하세요. 논리적으로 나열하여 순서대로 정리하세요.");
      List<ChatMessage> messages = new ArrayList<>();
      messages.add(systemPrompt);
      ChatRequest request = ChatRequest.builder()
              .model("gpt-3.5-turbo")
              .temperature(0.8f)
              .frequencyPenalty(0.7f)
              .presencePenalty(0.7f)
              .messages(messages).build();

      String key = mainActivity.chatGPTAPIKey;
      OpenAI openai = OpenAI.builder().apiKey(key).build();
      StringBuilder accumulatedResponse = new StringBuilder();
      messages.add(new ChatMessage(ChatUser.USER, prompt));

      for (ChatResponseChunk chunk : openai.streamChatCompletion(request)) {
        // This is nullable! ChatGPT will return null AT LEAST ONCE PER MESSAGE.
        String delta = chunk.get(0).getDeltaContent();
        if (delta != null){
          accumulatedResponse.append(delta);
          if (accumulatedResponse.length() > 0 &&
                  (accumulatedResponse.charAt(accumulatedResponse.length() - 1) == '.' ||
                          accumulatedResponse.charAt(accumulatedResponse.length() - 1) == '!' ||
                          accumulatedResponse.charAt(accumulatedResponse.length() - 1) == '?')) {
            String finalSentence = accumulatedResponse.toString().trim();
            handler.post(() -> speakText(finalSentence, 1, true));
            handler.post(() -> playMusic(false));
            //mainActivity.updateText(finalSentence, false);
            accumulatedResponse.setLength(0); // Reset for the next sentence
          }
        }

        // When the response is finished, we can add it to the messages list.
        if (chunk.get(0).isFinished()) {
          messages.add(chunk.get(0).getMessage());
          if (accumulatedResponse.length() > 0) {
            String remainingText = accumulatedResponse.toString().trim();
            handler.post(() -> speakText(remainingText, 1, true));
            stopGptStreaming();
            //mainActivity.updateText(remainingText, true);
          }
        }
      }
    });

    gptThread.start();
  }
  public void stopGptStreaming() {
    if (gptThread != null) {
      gptThread.interrupt();
      gptThread = null;
    }
  }

  public void playMusic(boolean start) {
    if (start) {
      mediaPlayer.start();
    } else {
      if (mediaPlayer != null) {
        mediaPlayer.stop();
        try {
          mediaPlayer.prepare();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }
  public void playDing() {
    soundPool.play(dingSoundId, 1, 1, 1, 0, 1);
  }
  public void playDing2() {
    soundPool.play(ding2SoundId, 1, 1, 1, 0, 1);
  }
}
