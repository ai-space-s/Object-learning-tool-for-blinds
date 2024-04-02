package com.wmakerlab.objectlearningtool;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import androidx.core.util.Pair;

public class AudioStreamer {
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    public AudioRecord audioRecord;
    private int bufferSize;
    public boolean isStreaming = false;
    private Thread streamingThread;
    private AzureSpeechHelper recognizer;

    @SuppressLint("MissingPermission")
    public AudioStreamer(AzureSpeechHelper recognizer) {
        this.recognizer = recognizer;
        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, FORMAT);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL, FORMAT, bufferSize);
    }
    @SuppressLint("MissingPermission")
    public void initializeAudioRecord() {
        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, FORMAT);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL, FORMAT, bufferSize);
    }

    public void startStreaming() {
        if (audioRecord == null) {
            initializeAudioRecord();
        }
        isStreaming = true;
        audioRecord.startRecording();
        recognizer.startRecognition();

//        streamingThread = new Thread(() -> {
//            byte[] data = new byte[bufferSize];
//            while (isStreaming) {
//                int read = audioRecord.read(data, 0, bufferSize);
//                if (read != AudioRecord.ERROR_INVALID_OPERATION) {
//                    // 여기서 data를 Azure Speech SDK로 전송할 수 있습니다.
//                }
//            }
//        }, "AudioStreamer Thread");
//        streamingThread.start();
    }

    public Pair<Boolean, String> stopStreaming(boolean cancel) {
        isStreaming = false;
        //audioRecord.stop();
        if(!cancel) {
            try {
                String recognizedText = recognizer.getRecognition();
                recognizer.stopRecognition();
                if (recognizedText.endsWith("인식")) {
                    int index = recognizedText.lastIndexOf("인식");
                    if (index != -1) {
                        recognizedText = recognizedText.substring(0, index);
                    }
                }
                boolean isTextValid = !recognizedText.trim().isEmpty() && recognizedText.length() > 2;
                return new Pair<>(isTextValid, recognizedText);
                //return new Pair<>(true, recognizedText);
            }
            catch(Exception e){
                return new Pair<>(true, "에러가 발생했습니다. 다시 말씀해 주세요.");
            }
        }
        else{
            return new Pair<>(false, "");
        }
    }
}
