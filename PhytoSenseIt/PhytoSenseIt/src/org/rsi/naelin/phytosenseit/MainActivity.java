package org.rsi.naelin.phytosenseit;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.ListIterator;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Core.MinMaxLocResult;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.imgcodecs.*;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

public class MainActivity extends Activity implements CvCameraViewListener2, OnTouchListener {
    private static final String TAG = "PhytoSenseIt::Activity";

    private CameraView mOpenCvCameraView;
    private List<Size> mResolutionList;
    private MenuItem[] mEffectMenuItems;
    private SubMenu mColorEffectsMenu;
    private MenuItem[] mResolutionMenuItems;
    private SubMenu mResolutionMenu;
    private Button mTakePhotoButton;
    private String mPhotoFilename;
    private ImageView mBinaryImageView;
    private Mat mImgMat;
    private Mat mTemplMat;
    private Mat mResultMat;
    private Bitmap mImgBmp;
    private Bitmap mTemplBmp;
    private Bitmap mResultBmp;
    private double[] mMaxValues = new double[3];
    private int x = 0;
    private Point[] mMaxLoc = new Point[3];
    
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                    //mOpenCvCameraView.setRotation(90);
                    mOpenCvCameraView.setOnTouchListener(MainActivity.this);
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    public MainActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Set views
        setContentView(R.layout.activity_main);  
        mOpenCvCameraView = (CameraView) findViewById(R.id.java_surface_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        // Button for detection process    
        mTakePhotoButton = (Button) findViewById(R.id.take_photo_button);
        mTakePhotoButton.setOnClickListener(new View.OnClickListener() {
	    
	    @Override
	    public void onClick(View v) {
		// Read template image from drawable as a bitmap
	        InputStream cs_templ = getResources().openRawResource(R.drawable.potato_commonscab_templ_1);
	        mTemplBmp = BitmapFactory.decodeStream(cs_templ);
		imageAcquisition();
		Log.d("onClick", "Round 1");
		
	        InputStream bs_templ = getResources().openRawResource(R.drawable.potato_blackscurf_templ_1);
	        mTemplBmp = BitmapFactory.decodeStream(bs_templ);
	        imageAcquisition();
		Log.d("onClick", "Round 2");

	        InputStream ss_templ = getResources().openRawResource(R.drawable.potato_silverscurf_templ_1);
	        mTemplBmp = BitmapFactory.decodeStream(ss_templ);
	        imageAcquisition();
		Log.d("onClick", "Round 3");

	        
	        int index = determinePossibleDisease(); 
	        if(index == 0) {
	            cs_templ = getResources().openRawResource(R.drawable.potato_commonscab_templ_1);
		    mTemplBmp = BitmapFactory.decodeStream(cs_templ);
		    Utils.bitmapToMat(mTemplBmp, mTemplMat);
	        }
	        else if(index == 1) {
		    bs_templ = getResources().openRawResource(R.drawable.potato_blackscurf_templ_1);
		    mTemplBmp = BitmapFactory.decodeStream(bs_templ);
		    Utils.bitmapToMat(mTemplBmp, mTemplMat);
	        }
	        else {
		    bs_templ = getResources().openRawResource(R.drawable.potato_silverscurf_templ_1);
		    mTemplBmp = BitmapFactory.decodeStream(bs_templ);
		    Utils.bitmapToMat(mTemplBmp, mTemplMat);
	        }
	        
	        displayResult(index);
	    }
	});
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
    }

    public void onCameraViewStopped() {
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        return inputFrame.rgba();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        List<String> effects = mOpenCvCameraView.getEffectList();

        if (effects == null) {
            Log.e(TAG, "Color effects are not supported by device!");
            return true;
        }

        mColorEffectsMenu = menu.addSubMenu("Color Effect");
        mEffectMenuItems = new MenuItem[effects.size()];

        int idx = 0;
        ListIterator<String> effectItr = effects.listIterator();
        while(effectItr.hasNext()) {
           String element = effectItr.next();
           mEffectMenuItems[idx] = mColorEffectsMenu.add(1, idx, Menu.NONE, element);
           idx++;
        }

        mResolutionMenu = menu.addSubMenu("Resolution");
        mResolutionList = mOpenCvCameraView.getResolutionList();
        mResolutionMenuItems = new MenuItem[mResolutionList.size()];

        ListIterator<Size> resolutionItr = mResolutionList.listIterator();
        idx = 0;
        while(resolutionItr.hasNext()) {
            Size element = resolutionItr.next();
            mResolutionMenuItems[idx] = mResolutionMenu.add(2, idx, Menu.NONE,
                    Integer.valueOf(element.width).toString() + "x" + Integer.valueOf(element.height).toString());
            idx++;
         }

        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "called onOptionsItemSelected; selected item: " + item);
        if (item.getGroupId() == 1)
        {
            mOpenCvCameraView.setEffect((String) item.getTitle());
            Toast.makeText(this, mOpenCvCameraView.getEffect(), Toast.LENGTH_SHORT).show();
        }
        else if (item.getGroupId() == 2)
        {
            int id = item.getItemId();
            Size resolution = mResolutionList.get(id);
            mOpenCvCameraView.setResolution(resolution);
            resolution = mOpenCvCameraView.getResolution();
            String caption = Integer.valueOf(resolution.width).toString() + "x" + Integer.valueOf(resolution.height).toString();
            Toast.makeText(this, caption, Toast.LENGTH_SHORT).show();
        }

        return true;
    }

    @SuppressLint("SimpleDateFormat")
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        Log.i(TAG,"onTouch event");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String currentDateandTime = sdf.format(new Date());
        mPhotoFilename = Environment.getExternalStorageDirectory().getPath() +
                               "/test_" + currentDateandTime + ".jpg";
        mOpenCvCameraView.takePicture(mPhotoFilename);
        Toast.makeText(this, mPhotoFilename + " saved", Toast.LENGTH_SHORT).show();
        return false;
    }
    
    public void preProcessing() {	
        //mImgMat = Imgcodecs.imread(mPhotoFilename, Imgcodecs.CV_LOAD_IMAGE_GRAYSCALE);

	// Convert test image to binary
        Imgproc.threshold(mImgMat,mImgMat,127,255,Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
        // Convert template to binary
        Imgproc.threshold(mTemplMat, mTemplMat, 127, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
        //Imgcodecs.imwrite(mPhotoFilename, mImgMat);
        /*
        // Convert mat to bmp
        Bitmap mImgBmp = Bitmap.createBitmap(mImgMat.cols(), mImgMat.rows(), Bitmap.Config.ARGB_8888);
        Log.d("imageProcessing", "Created Bitmap");
        Utils.matToBitmap(mImgMat, mImgBmp);
        Log.d("imageProcessing", "Converted Mat to Bitmap");
        
        // Close camera view
        mOpenCvCameraView.setVisibility(SurfaceView.GONE);

        // Display bmp in image view
        mBinaryImageView = (ImageView) findViewById(R.id.binary_image);
        mBinaryImageView.setImageBitmap(mImgBmp);*/
        templateMatching();
    }
    
    public void templateMatching() {
	/*
	// Load photo taken from camera app
	imgMat = Imgcodecs.imread(mPhotoFilename);
	*/
       
        // Apply template to mat image
	Imgproc.matchTemplate(mImgMat, mTemplMat, mResultMat, Imgproc.TM_CCOEFF_NORMED);
        Log.d("templateMatching", "Applied template matching");
        
        MinMaxLocResult mmr =  Core.minMaxLoc(mResultMat);
       
        // Save max/location into respective arrays
       // if(x == 1) // Running black scurf template
         //   mMaxValues[x] = mmr.maxVal - .2; // Scaling down the max values to even out with other disease maximums
        //else
            mMaxValues[x] = mmr.maxVal;
        Log.d("templateMatching", "Saved max into array");
        mMaxLoc[x] = mmr.maxLoc;
        Log.d("templateMatching", "Saved loc into array");

        x++;  
        
        Log.d("templateMatching", "Saved values into arrays");
        Log.d("templateMatching", "Max value: " + (mmr.maxVal));
        
        // Normalize
        Core.normalize( mResultMat, mResultMat, 0, 1, Core.NORM_MINMAX, -1, new Mat());
    }
    
    public void extractSaturationChannel() {	
	Log.d("extractSaturationChannel", "Run");
	 // Convert mat test image to HSV
        Imgproc.cvtColor(mImgMat, mImgMat, Imgproc.COLOR_BGR2HSV);
        Log.d("templateMatching", "Applied grayscale to test image");
        
        // Convert mat template to HSV
        Imgproc.cvtColor(mTemplMat, mTemplMat, Imgproc.COLOR_BGR2HSV);
        Log.d("templateMatching", "Applied grayscale to template image");
	
        // Split hue, saturation, and value
        List<Mat> imgChannels = new ArrayList<Mat>();
        List<Mat> templChannels = new ArrayList<Mat>();
        for(int i = 0; i < mImgMat.channels(); i++) {
            Mat channel = new Mat();
            imgChannels.add(channel);
            templChannels.add(channel);
        }
        Core.split(mImgMat, imgChannels);
        Core.split(mTemplMat, templChannels);
        
        mImgMat = imgChannels.get(1); // Get saturation channel for image
        mTemplMat = templChannels.get(1); // Get saturation channel for template  
        
        //preProcessing();
        templateMatching();
    }
    
    void imageAcquisition() {
	//Load test photo from drawable as a bitmap
        InputStream is = this.getResources().openRawResource(R.drawable.potato_commonscab_0);
        mImgBmp = BitmapFactory.decodeStream(is);
        Log.d("imageAcquisition", "Loaded test image from drawable");
     
        // Create new mat object for image
        mImgMat = new Mat();
        Log.d("imageAcquisition", "Created mat object for test");
        
        // Create new mat object for template
        mTemplMat = new Mat();
        Log.d("imageAcquisition", "Created mat object for template");
        
        // Convert bitmap test image to OpenCV mat 
        Utils.bitmapToMat(mImgBmp, mImgMat);
        Log.d("imageAcquisition", "Converted test bitmap to OpenCV mat");        
        
        // Create new mat object for resultMat
        int resultCols = mImgMat.cols() - mTemplMat.cols() + 1;
        int resultRows = mImgMat.rows() - mTemplMat.rows() + 1;
        mResultMat = new Mat(resultRows, resultCols, CvType.CV_32FC1);
        
        // Convert bitmap template to OpenCV mat 
        Utils.bitmapToMat(mTemplBmp, mTemplMat);
        Log.d("imageAcquisition", "Converted template bitmap to OpenCV mat");
        
        if(x == 2) // Last run: silver scurf test
            extractSaturationChannel();
        else {
            convertToGrayscale();
            templateMatching();
        }
    }
    
    void convertToGrayscale() {
        Imgproc.cvtColor(mImgMat, mImgMat, Imgproc.COLOR_BGR2GRAY);
        Imgproc.cvtColor(mTemplMat, mTemplMat, Imgproc.COLOR_BGR2GRAY);
    }
    
    void displayResult(int index) {

        // Draw a rectangle around the match
        Imgproc.rectangle(mImgMat, mMaxLoc[index], new Point(mMaxLoc[index].x + mTemplMat.cols(),
                mMaxLoc[index].y + mTemplMat.rows()), new Scalar(255,255,255));
       
        // Convert final mat image to bitmap
        mResultBmp = Bitmap.createBitmap(mImgMat.cols(), mImgMat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mImgMat, mResultBmp);
            
        // Close camera view
        mOpenCvCameraView.setVisibility(SurfaceView.GONE);
        
        // Display bmp in image view
        mBinaryImageView = (ImageView) findViewById(R.id.binary_image);
        mBinaryImageView.setImageBitmap(mResultBmp);
        
        CharSequence text;
        if(index == 0)
            text = "Common Scab disease detected";
        else if(index == 1)
            text = "Black Scurf disease detected";
        else
            text = "Silver Scurf disease detected";
        Toast toast = Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG);
        toast.show();
    }
    
    int determinePossibleDisease() {
	int maxIndexSoFar = 0;
	for(int i = 1; i < 3; i++) {
	    if(mMaxValues[maxIndexSoFar] < mMaxValues[i]) {
		maxIndexSoFar = i;
	    }
	}
	return maxIndexSoFar;
    }

}
