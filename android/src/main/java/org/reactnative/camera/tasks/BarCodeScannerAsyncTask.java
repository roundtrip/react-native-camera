package org.reactnative.camera.tasks;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

public class BarCodeScannerAsyncTask extends android.os.AsyncTask<Void, Void, Result> {
  private byte[] mImageData;
  private int mWidth;
  private int mHeight;
  private int mStride;
  private BarCodeScannerAsyncTaskDelegate mDelegate;
  private final MultiFormatReader mMultiFormatReader;
  private boolean mLimitScanArea;
  private float mScanAreaX;
  private float mScanAreaY;
  private float mScanAreaWidth;
  private float mScanAreaHeight;
  private int mCameraViewWidth;
  private int mCameraViewHeight;
  private float mRatio;

  //  note(sjchmiela): From my short research it's ok to ignore rotation of the image.
  public BarCodeScannerAsyncTask(
      BarCodeScannerAsyncTaskDelegate delegate,
      MultiFormatReader multiFormatReader,
      byte[] imageData,
      int width,
      int height,
      int stride,
      boolean limitScanArea,
      float scanAreaX,
      float scanAreaY,
      float scanAreaWidth,
      float scanAreaHeight,
      int cameraViewWidth,
      int cameraViewHeight,
      float ratio
  ) {
    mImageData = imageData;
    mWidth = width;
    mHeight = height;
    mStride = stride;
    mDelegate = delegate;
    mMultiFormatReader = multiFormatReader;
    mLimitScanArea = limitScanArea;
    mScanAreaX = scanAreaX;
    mScanAreaY = scanAreaY;
    mScanAreaWidth = scanAreaWidth;
    mScanAreaHeight = scanAreaHeight;
    mCameraViewWidth = cameraViewWidth;
    mCameraViewHeight = cameraViewHeight;
    mRatio = ratio;
  }

  @Override
  protected Result doInBackground(Void... ignored) {
    if (isCancelled() || mDelegate == null) {
      return null;
    }

    /**
     * mCameraViewWidth and mCameraViewHeight are obtained from portait orientation
     * mWidth and mHeight are measured with landscape orientation with Home button to the right
     * adjustedCamViewWidth is the adjusted width from the Aspect ratio setting
     */
    int adjustedCamViewWidth = (int) (mCameraViewHeight / mRatio);
    float adjustedScanY = (((adjustedCamViewWidth - mCameraViewWidth) / 2) + (mScanAreaY * mCameraViewWidth)) / adjustedCamViewWidth;
    
    int left = (int) (mScanAreaX * mWidth);
    int top = (int) (adjustedScanY * mHeight);
    int scanWidth = (int) (mScanAreaWidth * mWidth);
    int scanHeight = (int) (((mScanAreaHeight * mCameraViewWidth) / adjustedCamViewWidth) * mHeight);

    try {
      try {
        BinaryBitmap bitmap = generateBitmapFromImageData(
                mImageData,
                mWidth,
                mHeight,
                mStride,
                false,
                left,
                top,
                scanWidth,
                scanHeight
        );
        return mMultiFormatReader.decodeWithState(bitmap);
      } catch (NotFoundException e) {
      }

      try {
        BinaryBitmap bitmap = generateBitmapFromImageData(
                rotateImage(mImageData,mWidth, mHeight, mStride),
                mHeight,
                mWidth,
                mHeight,
                false,
                mHeight - scanHeight - top,
                left,
                scanHeight,
                scanWidth
        );
        return mMultiFormatReader.decodeWithState(bitmap);
      } catch (NotFoundException e) {
      }

      try {
        BinaryBitmap invertedBitmap = generateBitmapFromImageData(
                mImageData,
                mWidth,
                mHeight,
                mStride,
                true,
                mWidth - scanWidth - left,
                mHeight - scanHeight - top,
                scanWidth,
                scanHeight
        );
        return mMultiFormatReader.decodeWithState(invertedBitmap);
      } catch (NotFoundException e) {
      }

      try {
        BinaryBitmap invertedRotatedBitmap = generateBitmapFromImageData(
                rotateImage(mImageData,mWidth, mHeight, mStride),
                mHeight,
                mWidth,
                mHeight,
                true,
                top,
                mWidth - scanWidth - left,
                scanHeight,
                scanWidth
        );
        return mMultiFormatReader.decodeWithState(invertedRotatedBitmap);
      } catch (NotFoundException e) {
      }
    } catch (Throwable t) {
      t.printStackTrace();
    }

    // no barcode found
    return null;
  }

  private static byte[] rotateImage(byte[]imageData,int width, int height, int stride) {
    int newWidth = height;
    int newHeight = width;
    byte[] newImageData = new byte[newWidth * newHeight];

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        // Calculate the index for the original image with stride
        int oldIndex = y * stride + x;

        // Calculate new coordinates for counter-clockwise rotation
        int newX = y;
        int newY = (width - 1) - x;

        // Calculate the index for the new image (no stride needed for the output)
        int newIndex = newY * newWidth + newX;

        // Transfer the pixel
        newImageData[newIndex] = imageData[oldIndex];
      }
    }
    return newImageData;
  }

  @Override
  protected void onPostExecute(Result result) {
    super.onPostExecute(result);
    if (result != null) {
      mDelegate.onBarCodeRead(result, mWidth, mHeight, mImageData);
    }
    mDelegate.onBarCodeScanningTaskCompleted();
  }

  private BinaryBitmap generateBitmapFromImageData(byte[] imageData, int width, int height, int stride, boolean inverse, int left, int top, int sWidth, int sHeight) {
    PlanarYUVLuminanceSource source;
    if (mLimitScanArea) {
      source = new PlanarYUVLuminanceSource(
        imageData, // byte[] yuvData
        stride, // int dataWidth
        height, // int dataHeight
        left, // int left
        top, // int top
        sWidth, // int width
        sHeight, // int height
        false // boolean reverseHorizontal
      );
    } else {
      source = new PlanarYUVLuminanceSource(
        imageData, // byte[] yuvData
        stride, // int dataWidth
        height, // int dataHeight
        0, // int left
        0, // int top
        width, // int width
        height, // int height
        false // boolean reverseHorizontal
      );
    }
    if (inverse) {
      return new BinaryBitmap(new HybridBinarizer(source.invert()));
    } else {
      return new BinaryBitmap(new HybridBinarizer(source));
    }
  }
}
