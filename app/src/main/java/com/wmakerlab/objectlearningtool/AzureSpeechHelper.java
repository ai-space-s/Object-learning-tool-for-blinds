package com.wmakerlab.objectlearningtool;
import com.microsoft.cognitiveservices.speech.*;

public class AzureSpeechHelper {

    private static String SPEECH_SUBSCRIPTION_KEY = "NONE";
    private static String SERVICE_REGION = "koreacentral";

    public interface SpeechToTextCallback {
        void onResult(String text);
        void onError(String error);
    }
    public SpeechRecognizer recognizer;
    public StringBuilder recognizedText = new StringBuilder();
    public String recognizingText;
    private MainActivity mainActivity = MainActivity.getInstance();
    public boolean isError;

    public AzureSpeechHelper(String key, String region) {
        SPEECH_SUBSCRIPTION_KEY = key;
        SERVICE_REGION = region;
        SpeechConfig config = SpeechConfig.fromSubscription(SPEECH_SUBSCRIPTION_KEY, SERVICE_REGION);
        config.setSpeechRecognitionLanguage("ko-KR");
        recognizer = new SpeechRecognizer(config);
        recognizer.canceled.addEventListener((s, e) -> {
            if (e.getReason() == CancellationReason.Error) {
                if (e.getErrorCode() == CancellationErrorCode.ConnectionFailure) {
                    recognizedText.setLength(0);
                    recognizedText.append("인터넷 연결 상태가 좋지 않습니다.");
                    isError = true;
                }
            }
        });
        recognizer.recognizing.addEventListener((s, e) -> {
            if(mainActivity.recognizing) {
                recognizingText = e.getResult().getText();
                //mainActivity.updateText(recognizingText);
                isError = false;
            }
        });
        recognizer.recognized.addEventListener((s, e) -> {
            if(mainActivity.recognizing) {
                recognizingText = e.getResult().getText();
                if (containsString(recognizingText, "취소")) {
                    recognizedText.setLength(0);
                } else {
                    recognizedText.append(recognizingText);
                    mainActivity.playDing2();
                }
                mainActivity.updateText(recognizedText.toString());
            }
        });
    }
    public boolean containsString(String sourceString, String searchString) {
        // null check
        if (sourceString == null || searchString == null) {
            return false;
        }

        // Check if the source string contains the search string
        return sourceString.contains(searchString);
    }
    public void startRecognition() {
        recognizedText.setLength(0);
        recognizingText = "";
        recognizer.getProperties().setProperty(PropertyId.SpeechServiceConnection_InitialSilenceTimeoutMs, "10000");
        recognizer.getProperties().setProperty(PropertyId.SpeechServiceConnection_EndSilenceTimeoutMs, "10000");
        recognizer.startContinuousRecognitionAsync();
    }

    public String getRecognition() {
        //recognizer.stopContinuousRecognitionAsync();
        recognizedText.append(recognizingText);
        return recognizedText.toString();
        //return recognizingText;
    }
    public void stopRecognition() {
        recognizer.stopContinuousRecognitionAsync();
    }
    public static void speechToText(SpeechToTextCallback callback) {
        SpeechConfig config = SpeechConfig.fromSubscription(SPEECH_SUBSCRIPTION_KEY, SERVICE_REGION);
        config.setSpeechRecognitionLanguage("ko-KR");
        SpeechRecognizer recognizer = new SpeechRecognizer(config);

        try {
            SpeechRecognitionResult result = recognizer.recognizeOnceAsync().get();

            if (result.getReason() == ResultReason.RecognizedSpeech) {
                callback.onResult(result.getText());
            } else if (result.getReason() == ResultReason.NoMatch) {
                callback.onError("일치하는 텍스트가 없습니다.");
            } else if (result.getReason() == ResultReason.Canceled) {
                CancellationDetails cancellation = CancellationDetails.fromResult(result);
                callback.onError("요청이 취소되었습니다. Reason: " + cancellation.getReason());
            }
        } catch (Exception e) {
            callback.onError("오류 발생: " + e.getMessage());
        }
    }
    public static String speechToTextSync() {
        SpeechConfig config = SpeechConfig.fromSubscription(SPEECH_SUBSCRIPTION_KEY, SERVICE_REGION);
        config.setSpeechRecognitionLanguage("ko-KR");
        SpeechRecognizer recognizer = new SpeechRecognizer(config);
        try {
            SpeechRecognitionResult result = recognizer.recognizeOnceAsync().get();
            if (result.getReason() == ResultReason.RecognizedSpeech) {
                return result.getText();
            } else {
                return null; // 또는 적절한 오류 메시지를 반환
            }
        } catch (Exception e) {
            return null; // 또는 적절한 오류 메시지를 반환
        }
    }
//    public static void getTextFromAudioFile(String fileName, SpeechToTextCallback callback) {
//        SpeechConfig config = SpeechConfig.fromSubscription(SPEECH_SUBSCRIPTION_KEY, SERVICE_REGION);
//        config.setSpeechRecognitionLanguage("ko-KR");
//        String wavFileName = convertM4aToWav(fileName);
//        AudioConfig audioConfig = AudioConfig.fromWavFileInput(wavFileName);
//        SpeechRecognizer recognizer = new SpeechRecognizer(config, audioConfig);
//        try {
//            SpeechRecognitionResult result = recognizer.recognizeOnceAsync().get();
//
//            if (result.getReason() == ResultReason.RecognizedSpeech) {
//                callback.onResult(result.getText());
//            } else if (result.getReason() == ResultReason.NoMatch) {
//                callback.onError("일치하는 텍스트가 없습니다.");
//            } else if (result.getReason() == ResultReason.Canceled) {
//                CancellationDetails cancellation = CancellationDetails.fromResult(result);
//                callback.onError("요청이 취소되었습니다. Reason: " + cancellation.getReason());
//            }
//        } catch (Exception e) {
//            callback.onError("오류 발생: " + e.getMessage());
//        }
//    }
//    public static String convertM4aToWav(String m4aFilePath) {
//        String wavFilePath = m4aFilePath.replace(".m4a", ".wav");
//        int returnCode = FFmpeg.execute("-i " + m4aFilePath + " " + wavFilePath);
//        if (returnCode == 0) {
//            return wavFilePath;
//        } else {
//            return null;
//        }
//    }
}