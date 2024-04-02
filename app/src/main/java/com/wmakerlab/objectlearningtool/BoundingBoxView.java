package com.wmakerlab.objectlearningtool;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static android.app.Activity.RESULT_OK;

public class BoundingBoxView extends View {
    private static final String BOUNDING_BOX_PREFERENCES = "bounding_box_preferences";
    private static final String KEY_BOUNDING_BOXES_COUNT = "bounding_boxes_count";
    private static final String KEY_BOUNDING_BOX_PREFIX = "bounding_box_";

    private ArrayList<LabeledRectF> boundingBoxes = new ArrayList<>();
    private LabeledRectF currentBoundingBox = null;
    private Paint boundingBoxPaint;
    private Paint boundingBoxWithLabelPaint;
    private Paint touchCirclePaint;
    private Paint labelPaint;
    private static final int READ_REQUEST_CODE = 42;
    private static final int WRITE_REQUEST_CODE = 43;
    private Activity activityContext;
    private int height;
    private int width;
    private int canvasWidth;
    private int canvasHeight;
    public int statusbarHeight;
    public int layoutHeight;
    public int offset;
    //public WindowManager.LayoutParams overlayImageParams;
    public int xParams;
    public int yParams;
    public int widthParams;
    public int heightParams;
    public int touchThreshold;
    public boolean drawCircleMode;
    private BoundingBoxesLoadedCallback boundingBoxesLoadedCallback;

    public BoundingBoxView(Context context) {
        super(context);
        init();
    }

    public BoundingBoxView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        boundingBoxPaint = new Paint();
        boundingBoxPaint.setColor(Color.RED);
        boundingBoxPaint.setStyle(Paint.Style.STROKE);
        boundingBoxPaint.setStrokeWidth(3);
        boundingBoxWithLabelPaint = new Paint();
        boundingBoxWithLabelPaint.setColor(Color.BLUE);
        boundingBoxWithLabelPaint.setStyle(Paint.Style.STROKE);
        boundingBoxWithLabelPaint.setStrokeWidth(3);
        touchCirclePaint = new Paint();
        touchCirclePaint.setColor(Color.MAGENTA);
        touchCirclePaint.setStyle(Paint.Style.STROKE);
        boundingBoxWithLabelPaint.setStrokeWidth(3);
        labelPaint = new Paint();
        labelPaint.setColor(Color.BLACK);
        labelPaint.setTextSize(30);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        height = displayMetrics.heightPixels;
        width = displayMetrics.widthPixels;
        offset = 0;
    }
    public int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }
    public void updateCurrentBoundingBox(PointF point1, PointF point2) {
//        float left = Math.min(point1.x, point2.x) / width * canvasWidth;
//        float top = Math.min(point1.y, point2.y) / height * canvasHeight;
//        float right = Math.max(point1.x, point2.x) / width * canvasWidth;
//        float bottom = Math.max(point1.y, point2.y) / height * canvasHeight;
        float left = Math.min(point1.x, point2.x);
        float top = Math.min(point1.y, point2.y);
        float right = Math.max(point1.x, point2.x);
        float bottom = Math.max(point1.y, point2.y);
        currentBoundingBox = new LabeledRectF(left, top, right, bottom, "");
        invalidate();
    }
    public void updateBoundingBox(){
        invalidate();
    }
    public interface BoundingBoxesLoadedCallback {
        void onBoundingBoxesLoaded();
    }
    public void finalizeCurrentBoundingBox() {
        if (currentBoundingBox != null) {
            boundingBoxes.add(currentBoundingBox);
            debugBoundingBox(currentBoundingBox);
            currentBoundingBox = null;
            invalidate();
        }
    }
    public void debugBoundingBox(RectF bbox){
        System.out.println(bbox.left + ", " + bbox.top + ", " + bbox.right + ", "  + bbox.bottom);
    }
    public void setActivityContext(Activity activityContext) {
        this.activityContext = activityContext;
    }
    public void updateParams(WindowManager.LayoutParams params){
        xParams = params.x;
        yParams = params.y;
        widthParams = params.width;
        heightParams = params.height;
    }
    public void saveBoundingBoxes(WindowManager.LayoutParams params) {
        updateParams(params);
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "dinosaur_bounding_box.json");

        activityContext.startActivityForResult(intent, WRITE_REQUEST_CODE);
    }

    public void loadBoundingBoxes() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        activityContext.startActivityForResult(intent, READ_REQUEST_CODE);
    }
    public void loadBoundingBoxes(BoundingBoxesLoadedCallback callback) {
        this.boundingBoxesLoadedCallback = callback;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        activityContext.startActivityForResult(intent, READ_REQUEST_CODE);
        //callback.onBoundingBoxesLoaded();
    }
//    public void saveBoundingBoxes() {
//        SharedPreferences preferences = getContext().getSharedPreferences(BOUNDING_BOX_PREFERENCES, Context.MODE_PRIVATE);
//        SharedPreferences.Editor editor = preferences.edit();
//        editor.putInt(KEY_BOUNDING_BOXES_COUNT, boundingBoxes.size());
//
//        for (int i = 0; i < boundingBoxes.size(); i++) {
//            RectF boundingBox = boundingBoxes.get(i);
//            editor.putFloat(KEY_BOUNDING_BOX_PREFIX + i + "_left", boundingBox.left / width);
//            editor.putFloat(KEY_BOUNDING_BOX_PREFIX + i + "_top", boundingBox.top / height);
//            editor.putFloat(KEY_BOUNDING_BOX_PREFIX + i + "_right", boundingBox.right / width);
//            editor.putFloat(KEY_BOUNDING_BOX_PREFIX + i + "_bottom", boundingBox.bottom / height);
//        }
//
//        editor.apply();
//    }
//    public void loadBoundingBoxes() {
//        SharedPreferences preferences = getContext().getSharedPreferences(BOUNDING_BOX_PREFERENCES, Context.MODE_PRIVATE);
//        int count = preferences.getInt(KEY_BOUNDING_BOXES_COUNT, 0);
//        boundingBoxes.clear();
//
//        for (int i = 0; i < count; i++) {
//            float left = preferences.getFloat(KEY_BOUNDING_BOX_PREFIX + i + "_left", 0) * width;
//            float top = preferences.getFloat(KEY_BOUNDING_BOX_PREFIX + i + "_top", 0) * height;
//            float right = preferences.getFloat(KEY_BOUNDING_BOX_PREFIX + i + "_right", 0) * width;
//            float bottom = preferences.getFloat(KEY_BOUNDING_BOX_PREFIX + i + "_bottom", 0) * height;
//            RectF boundingBox = new RectF(left, top, right, bottom);
//            boundingBoxes.add(boundingBox);
//        }
//
//        invalidate();
//    }

    public void handleActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == READ_REQUEST_CODE && resultCode == RESULT_OK) {
            if (resultData != null) {
                Uri uri = resultData.getData();
                loadBoundingBoxesFromFile(activityContext, uri);
                if (this.boundingBoxesLoadedCallback != null) {
                    this.boundingBoxesLoadedCallback.onBoundingBoxesLoaded();
                    this.boundingBoxesLoadedCallback = null;
                }
            }
        } else if (requestCode == WRITE_REQUEST_CODE && resultCode == RESULT_OK) {
            if (resultData != null) {
                Uri uri = resultData.getData();
                saveBoundingBoxesToFile(activityContext, uri);
            }
        }
    }

    private void saveBoundingBoxesToFile(Context context, Uri uri) {
        // BoundingBoxes JSON 생성
        List<LabeledRectF> normalizedBoundingBoxes = getBoundingBoxes();
        Gson gson = new Gson();
        String jsonBoundingBoxes = gson.toJson(normalizedBoundingBoxes);

        // OverlayImageParams JSON 생성
        HashMap<String, Integer> overlayParamsMap = new HashMap<>();
        overlayParamsMap.put("x", xParams);
        overlayParamsMap.put("y", yParams);
        overlayParamsMap.put("width", widthParams);
        overlayParamsMap.put("height", heightParams);
        String jsonOverlayParams = gson.toJson(overlayParamsMap);

        // 최종적으로 이 두 가지 JSON 문자열을 하나의 JSON 객체로 만듭니다.
        HashMap<String, String> combinedMap = new HashMap<>();
        combinedMap.put("BoundingBoxes", jsonBoundingBoxes);
        combinedMap.put("OverlayImageParams", jsonOverlayParams);
        String combinedJson = gson.toJson(combinedMap);

        // 최종 JSON 문자열을 파일로 저장합니다.
        try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri)) {
            outputStream.write(combinedJson.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadBoundingBoxesFromFile(Context context, Uri uri) {
        Gson gson = new Gson();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.getContentResolver().openInputStream(uri)))) {
            // Combined JSON 읽기
            Type combinedType = new TypeToken<HashMap<String, String>>() {}.getType();
            HashMap<String, String> combinedMap = gson.fromJson(reader, combinedType);

            // BoundingBoxes JSON 변환
            String jsonBoundingBoxes = combinedMap.get("BoundingBoxes");
            Type boundingBoxListType = new TypeToken<ArrayList<LabeledRectF>>() {}.getType();
            List<LabeledRectF> boundingBoxes = gson.fromJson(jsonBoundingBoxes, boundingBoxListType);

            // OverlayImageParams JSON 변환
            String jsonOverlayParams = combinedMap.get("OverlayImageParams");
            Type overlayParamsType = new TypeToken<HashMap<String, Integer>>() {}.getType();
            HashMap<String, Integer> overlayParamsMap = gson.fromJson(jsonOverlayParams, overlayParamsType);

            xParams = overlayParamsMap.get("x");
            yParams = overlayParamsMap.get("y");
            widthParams = overlayParamsMap.get("width");
            heightParams = overlayParamsMap.get("height");

            // boundingBoxes 변수와 overlayImageParams에 필요한 작업 추가
            setBoundingBoxes(boundingBoxes);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void clearBoundingBoxes() {
        boundingBoxes.clear();
        invalidate();
    }
    public void removeLastBoundingBox() {
        if (!boundingBoxes.isEmpty()) {
            boundingBoxes.remove(boundingBoxes.size() - 1);
            invalidate();
        }
    }

    public ArrayList<LabeledRectF> getBoundingBoxes() {
        ArrayList<LabeledRectF> normalizedBoundingBoxes = new ArrayList<>();
        for (LabeledRectF boundingBox : boundingBoxes) {
            debugBoundingBox(boundingBox);
            float left = boundingBox.left / width;
            float top = (boundingBox.top - statusbarHeight - layoutHeight) / height;
            float right = boundingBox.right / width;
            float bottom = (boundingBox.bottom - statusbarHeight - layoutHeight) / height;
            LabeledRectF newBoundingBox = new LabeledRectF(left, top, right, bottom, boundingBox.getLabel());
            debugBoundingBox(newBoundingBox);
            //normalizedBoundingBoxes.add(new RectF(left, top, right, bottom));
            normalizedBoundingBoxes.add(newBoundingBox);
        }
        return normalizedBoundingBoxes;
    }
    public ArrayList<LabeledRectF> getRawBoundingBoxes() {
        return (ArrayList<LabeledRectF>) boundingBoxes;
    }
//    public ArrayList<RectF> getBoundingBoxes(int cameraStart) {
//        ArrayList<RectF> normalizedBoundingBoxes = new ArrayList<>();
//        for (RectF boundingBox : boundingBoxes) {
//            debugBoundingBox(boundingBox);
//            float left = boundingBox.left / width;
//            float top = (boundingBox.top - ((statusbarHeight + layoutHeight + offset) * cameraStart)) / height;
//            float right = boundingBox.right / width;
//            float bottom = (boundingBox.bottom - ((statusbarHeight + layoutHeight + offset) * cameraStart)) / height;
//            RectF newBoundingBox = new RectF(left, top, right, bottom);
//            debugBoundingBox(newBoundingBox);
//            //normalizedBoundingBoxes.add(new RectF(left, top, right, bottom));
//            normalizedBoundingBoxes.add(newBoundingBox);
//        }
//        return normalizedBoundingBoxes;
//    }
    public void setBoundingBoxes(List<LabeledRectF> boundingBoxes) {
        this.boundingBoxes = new ArrayList<>();
        for (LabeledRectF normalizedBoundingBox : boundingBoxes) {
            float left = normalizedBoundingBox.left * width;
            float top = normalizedBoundingBox.top * height + statusbarHeight + layoutHeight;
            float right = normalizedBoundingBox.right * width;
            float bottom = normalizedBoundingBox.bottom * height + statusbarHeight + layoutHeight;
            this.boundingBoxes.add(new LabeledRectF(left, top, right, bottom, normalizedBoundingBox.getLabel()));
        }
        invalidate();
    }
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        canvasWidth = w;
        canvasHeight = h;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (LabeledRectF boundingBox : boundingBoxes) {
            if (!boundingBox.getLabel().isEmpty()) {
                canvas.drawRect(boundingBox, boundingBoxWithLabelPaint);
                float x = boundingBox.centerX();
                float y = boundingBox.centerY();
                canvas.drawText(boundingBox.getLabel(), x, y, labelPaint);
            } else {
                canvas.drawRect(boundingBox, boundingBoxPaint);
            }
        }
        if(drawCircleMode) {
            canvas.drawCircle(xParams, yParams, touchThreshold, touchCirclePaint);
            canvas.drawCircle(xParams + widthParams, yParams, touchThreshold, touchCirclePaint);
            canvas.drawCircle(xParams, yParams + heightParams, touchThreshold, touchCirclePaint);
            canvas.drawCircle(xParams + widthParams, yParams + heightParams, touchThreshold, touchCirclePaint);
        }
        if (currentBoundingBox != null) {
            canvas.drawRect(currentBoundingBox, boundingBoxPaint);
        }
    }

//    @Override
//    protected void onDraw(Canvas canvas) {
//        super.onDraw(canvas);
//
//        for (RectF boundingBox : boundingBoxes) {
//            float left = boundingBox.left / width * canvasWidth;
//            float top = boundingBox.top / height * canvasHeight;
//            float right = boundingBox.right / width * canvasWidth;
//            float bottom = boundingBox.bottom / height * canvasHeight;
//            canvas.drawRect(left, top, right, bottom, boundingBoxPaint);
//        }
//        if (currentBoundingBox != null) {
//            float left = currentBoundingBox.left / width * canvasWidth;
//            float top = currentBoundingBox.top / height * canvasHeight;
//            float right = currentBoundingBox.right / width * canvasWidth;
//            float bottom = currentBoundingBox.bottom / height * canvasHeight;
//            canvas.drawRect(left, top, right, bottom, boundingBoxPaint);
//        }
//    }
}