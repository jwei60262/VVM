package com.att.mobile.android.vvm.screen;

import java.security.InvalidParameterException;
import java.util.LinkedList;
import java.util.Random;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

public class DotMeterView extends View {

	private String TAG = "DotMeterView";

	private static int EQUALIZER_MIN_WIDTH_DOTS = 12;   // the minimal equalizer's width in dots
	private static int EQUALIZER_HEIGHT_DOTS = 8;            // equalizer's height in dots

	private static int DOT_DIAMETER;

	private Paint paint;
	private int equalizerWidthDots = 0;                                              // equalizer's width in dots 
	private int dotRadius = 0;                                             
	private int bottomRowCenterY = 0;                                                // the y coordinate of the center of the bottom's row of the equalizer
	private final static int COLOR_CHG_STEP = 255 / (EQUALIZER_HEIGHT_DOTS - 1);     // step of equalizer's color changing 
	private static final int SIGNAL_HALF_WIDTH_DOTS = 3;                             // the half of the width of a equalizer's signal in dots(signal of a recorded sound)

	private LinkedList<Integer> equalizerState = new LinkedList<Integer>();                  // For equalizer:  |=  =     =  |   equalizerValues list: {3, 2, 1, 3, 0, 2, 2, 1, 3, 0, 2}
	//                 |== = = = = =|
	//                 |==== = === =|
	//                 |------------|

	private boolean isEqualizerActive = false;
	private boolean isColored = false;
	private static final int INACTIVE_EQUALIZER_DOT_COLOR = Color.LTGRAY;
	private static final int INACTIVE_EQUALIZER_DOT_COLOR_ON = Color.GRAY;

	// Refresh handler message constant
	private static final int REFRESH_TICK_OFF = 0;	
	private static final int REFRESH_TICK_ON = 1;
	private static final int REFRESH_TICK_KILL = 2;

	// Frame rate of meter update animation
	private static final int FRAME_RATE = 20;

	private Canvas canvas;
	private Random random = new Random();



	public DotMeterView(Context context) {
		super(context);
		initDrawing();
	}	

	public DotMeterView(Context context, AttributeSet attrSet) {
		super(context, attrSet);
		initDrawing();
	}

	public DotMeterView(Context context, AttributeSet attrSet, int defStyle) {
		super(context, attrSet, defStyle);
		initDrawing();
	}

	/**
	 * Initialization of drawing
	 */
	private void initDrawing() {
		paint = new Paint();
		paint.setStyle(Paint.Style.FILL);
		paint.setColor(Color.WHITE);
		paint.setFilterBitmap(false);
		paint.setDither(false);

//		Log.i(TAG, "Initialization of equalizers drawing");
	}

	/**
	 * Draw a dot of the equalizer 
	 * @param canvas The canvas item
	 * @param x The dot's x coordinate
	 * @param y The dot's y coordinate
	 * @param radius The dot's radius
	 * @param color The dots color
	 */
	private void drawDot(final int x, final int y, final int radius, final int color) {
		if(x < 0)
			throw new IllegalArgumentException("The given x coordinate of the circle's center is negative");
		if(y < 0)
			throw new IllegalArgumentException("The given y coordinate of the circle's center is negative");		

		if(radius < 0)
			throw new IllegalArgumentException("The given circle's radius is negative");

		paint.setColor(color);
		canvas.drawCircle(x, y, radius, paint);
	}

	/**
	 * Draw a row of equalizer
	 * @param rowCenterY The y coordinate of the row's center
	 * @param dotRadius The radius of the equalizer's dot
	 * @param canvas The given canvas item
	 * @param color The color of the row
	 */
	private void drawEqualizerRow(final int rowCenterY, final int dotRadius, final int color) {
		if(rowCenterY < 0)
			throw new IllegalArgumentException("The given y coordinate of a equalizer's row center is negative");

		if(dotRadius < 0)
			throw new IllegalArgumentException("The given circle's radius is negative");

		int rowX = dotRadius;
		for(int i = 0; i < equalizerWidthDots; ++i) {
			drawDot(rowX, rowCenterY, dotRadius, color);
			rowX += DOT_DIAMETER;
		}
	}

	/**
	 * Initialization of the equalizers sizes and its dot size.
	 * Decrease the dot's radius till integer number of dots for the equalizer's width 
	 * and the number of dots for the equalizer's height equals to QUALIZER_HEIGHT_DOTS
	 */
	private void initEqualizerSizes() {
		final int viewWidth = getWidth(),
		viewHeight = getHeight();

		equalizerWidthDots = EQUALIZER_MIN_WIDTH_DOTS;
		dotRadius                = viewWidth / EQUALIZER_MIN_WIDTH_DOTS / 2;		
		while(equalizerWidthDots * dotRadius * 2 != viewWidth || EQUALIZER_HEIGHT_DOTS * dotRadius * 2 > viewHeight) {
			equalizerWidthDots++;
			dotRadius = viewWidth / equalizerWidthDots / 2;
		}				

		DOT_DIAMETER = dotRadius * 2;

		bottomRowCenterY = viewHeight - dotRadius; 
//		Log.i(TAG, "Initialization of the equalizer. Equalizer width in dots: " + equalizerWidthDots + ", height: " + EQUALIZER_HEIGHT_DOTS 
//				+ ", dot radius: " + dotRadius);
	}

	/**
	 * Draw dots of the equalizer without coloring
	 */
	private void drawEmptyEqualizer() {
		for(int rowNum = 0; rowNum < EQUALIZER_HEIGHT_DOTS; ++rowNum)
			drawEqualizerRow(bottomRowCenterY - rowNum * DOT_DIAMETER, dotRadius, Color.WHITE);		

//		Log.i(TAG, "Drawing an empty equalizer");
	}

	/**
	 * Draw equalizer on the given canvas item
	 * @param canvas The given canvas item
	 */
	private void drawEqualizer() {
		drawEmptyEqualizer();

		if(!equalizerState.isEmpty())
			for(int column = 0; column < equalizerWidthDots; ++column) {
				int curEqualizerVal = equalizerState.get(column).intValue(); 
				if(curEqualizerVal != 0)
					setColumnColors(column, curEqualizerVal);
			}

//		Log.i(TAG, "Draw the equalizer");
	}

	private int clamp(final int val, final int low, final int high) {
		if (val < low) 
			return low;
		if (val > high) 
			return high;

		return val;
	}

	/**
	 * Set equalizer active(e.g. for recording)
	 */
	public void activate(boolean isColored) {
		if(!isEqualizerActive) {

			isEqualizerActive = true;
			startRefreshingLoop();
		}
		this.isColored = isColored;
	}

	/**
	 * Deactivate the equalizer
	 */
	public void deactivate() {
		if(isEqualizerActive){
			isEqualizerActive = false;
			// Be sure to invalidate one last time to go back to gray
			invalidate();
			stopRefreshingLoop();
		}
	}

	public int[] setEqualizerValues(final int currentEqualizerValue) {
		if(currentEqualizerValue < 0)
			throw new InvalidParameterException("The given current meter value is negative");

		if(currentEqualizerValue == 0)
			return null;

		final int maxSignalCenterCol = equalizerWidthDots - 2 * SIGNAL_HALF_WIDTH_DOTS;
//		Log.i(TAG, "Width " + equalizerWidthDots);
		if(maxSignalCenterCol < 0)
			return null;

		int[] values = new int[equalizerState.size()];
		
		
		final int col = SIGNAL_HALF_WIDTH_DOTS + random.nextInt(maxSignalCenterCol);
		equalizerState.set(col, Integer.valueOf(clamp((int) currentEqualizerValue, 1, EQUALIZER_HEIGHT_DOTS)));
		values[col] = equalizerState.get(col);
		
		Random r = new Random();
		for(int i = 1; i <= SIGNAL_HALF_WIDTH_DOTS; ++i) {
			equalizerState.set(col - i, Integer.valueOf(clamp((int) currentEqualizerValue - (SIGNAL_HALF_WIDTH_DOTS - i)
					- (r.nextInt() * 2), 0, EQUALIZER_HEIGHT_DOTS)));
			values[col] = equalizerState.get(col-i);
			equalizerState.set(col + i, Integer.valueOf(clamp((int) currentEqualizerValue - (i - 1)
					- (r.nextInt() * 2), 0, EQUALIZER_HEIGHT_DOTS)));
			values[col] = equalizerState.get(col+i);
		}
		return values;
	}
	
	public void setEqualizerValues(int[] currEqualizerState){
		for(int i = 0; i < currEqualizerState.length; ++i) {
			setEqualizerValues(currEqualizerState[i]);
		}
	}

	/**
	 * Decrease all the levels of the equalizer
	 */
	private void decreaseEqualizerValues() {
		if(!equalizerState.isEmpty())
			for(int i = 0; i < equalizerWidthDots; ++i) {				
				int value = equalizerState.get(i).intValue();
				if(value > 0)
					equalizerState.set(i, Integer.valueOf(value - 1));
			}
	}

	// Handler that will update values necessary to draw the effect, and call
	// itself
	// based on a desired frame rate (FRAME_RATE)
	private Handler refreshHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case REFRESH_TICK_ON:
				decreaseEqualizerValues();
				invalidate();
				refreshHandler.sendEmptyMessageDelayed(REFRESH_TICK_ON, 1000/ FRAME_RATE);
				break;
			case REFRESH_TICK_OFF:
				break;
			case REFRESH_TICK_KILL:			
				// handle last call
				refreshHandler.removeMessages(REFRESH_TICK_ON);
				refreshHandler.removeMessages(REFRESH_TICK_OFF);
				break;
			default:
				break;
			}
		}
	};	

	/**
	 * startRefreshingLoop() start refreshing loop by sending the first message
	 */
	public void startRefreshingLoop(){
		this.refreshHandler.sendEmptyMessage(REFRESH_TICK_ON);
	}
	/**
	 * stopRefreshingLoop() stop the refreshing loop 
	 */
	public void stopRefreshingLoop(){
		this.refreshHandler.sendEmptyMessage(REFRESH_TICK_OFF);
	}	
	/**
	 * killRefreshingLoop() terminate refreshing loop by sending termination message 
	 */
	public void killRefreshingLoop(){
		this.refreshHandler.sendEmptyMessage(REFRESH_TICK_KILL);
	}

	protected void onDetachedFromWindow() {
		this.killRefreshingLoop();
	};

	/**
	 * Initialization of the array for equalizer values for all columns.
	 * All values will be set to 1 
	 */
	public void initEqualizerValues() {
		if(!equalizerState.isEmpty())
			equalizerState.clear();
		for(int i = 0; i < equalizerWidthDots; ++i)
			equalizerState.add(Integer.valueOf(0));

//		Log.i(TAG, "Initialization of the equalizer's state array");
	}

	/**
	 * Coloring the column of equalizer. The coloring is done from the second row from the equalizer's bottom. The column colored by ascending green part 
	 * of RGB for ascending order of dots     
	 * @param column The column's number within equalizer
	 * @param dotsNumForColoring The number of dots within the column which should be colored
	 * @param canvas The canvas for drawing
	 */
	private void setColumnColors(final int column, final int dotsNumForColoring) {
		if( (column > equalizerWidthDots) || (column < 0) )
			throw new InvalidParameterException("The number of column " + column + " is invalid");
		if( (dotsNumForColoring > EQUALIZER_HEIGHT_DOTS) || (dotsNumForColoring < 0) )
			throw new InvalidParameterException("The number of dots in column for coloring " + dotsNumForColoring + " is invalid");

		final int columnCenterX = (column == 0) ? dotRadius: column * DOT_DIAMETER - dotRadius;
		for(int rowNum = 0; rowNum < dotsNumForColoring; ++rowNum) {


			int curColor = INACTIVE_EQUALIZER_DOT_COLOR;
			if (isEqualizerActive){
				curColor = INACTIVE_EQUALIZER_DOT_COLOR_ON;
			}
			if (isColored){
				curColor = Color.rgb(255, rowNum * COLOR_CHG_STEP, 0); 
			}

			drawDot(columnCenterX, bottomRowCenterY - rowNum * DOT_DIAMETER, dotRadius, curColor);
		}
	}	

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		this.canvas = canvas;

		try {		
			initEqualizerSizes();

			if ( equalizerState.isEmpty() || (equalizerState.size() != equalizerWidthDots) )
				initEqualizerValues();

			drawEqualizer();
		}
		catch(Exception e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}	

	//	public void setDensity(float d){
	//	if (d == HIGH_DENSITY){
	//		this.density = HIGH_DENSITY;
	//	}
	//	else{
	//		this.density = MID_DENSITY;
	//	}
	// }

	//	/**
	//	 * setDotsColor() choose gray (false) or colored (true) dots in the Equalizer 
	//	 * @param colored = true/false
	//	 */
	//	public void setDotsColor(boolean colored) {	
	//		isEqualizerActive = colored;
	//	}

	public void cancelDots(int cancelDotsNum) {}

	//	private Context _context;
	//
	//	// Number of vertical bars in the meter
	//	private static final int NUM_BARS = 15;
	//	private int[] barValues;
	//	private Paint paint;
	//	private Bitmap[] dot;
	//	private Bitmap dotOff;
	//	private Bitmap dotBg;
	//	private Matrix paintMatrix;
	//	private final static float HIGH_DENSITY = 1.5f;
	//	private final static float MID_DENSITY = 1.0f;
	//	private float density = MID_DENSITY;
	//	
	//	// Refresh handler message constant
	//	private static final int REFRESH_TICK_OFF = 0;	
	//	private static final int REFRESH_TICK_ON = 1;
	//	private static final int REFRESH_TICK_KILL = 2;
	//
	//	// Frame rate of meter update animation
	//	private static final int FRAME_RATE = 20;
	//	
	//	/* If there is not enough place for 8 rows of dots we need to cancel rows */
	//	private int canceledDots = 0;
	//	
	//	private boolean isActive = false;
	//
	//	public DotMeterView(Context context) {
	//		super(context);
	//		_context = context;
	//		init();
	//	}
	//
	//	public DotMeterView(Context context, AttributeSet attrs) {
	//		super(context, attrs);
	//		_context = context;
	//		init();
	//	}
	//
	//	public DotMeterView(Context context, AttributeSet attrs, int defStyle) {
	//		super(context, attrs, defStyle);
	//		_context = context;
	//		init();
	//	}
	//
	//	private void init() {
	//		barValues = new int[NUM_BARS];
	//		for (int i = 0; i < NUM_BARS; i++) {
	//			barValues[i] = 0;
	//		}
	//		// white dot as BG
	//		dotBg = BitmapFactory.decodeResource(_context.getResources(),
	//				R.drawable.new_voicemessage_equalizer_bg);
	//		// gray dot for inactive state
	//		dotOff = BitmapFactory.decodeResource(_context.getResources(),
	//				R.drawable.new_voicemessage_equalizer_inactive);
	//		setDotsColor(false);
	//
	//		// used to handle scaling and translation of dots in meter
	//		paintMatrix = new Matrix();
	//
	//		// our paint
	//		paint = new Paint();
	//		paint.setColor(Color.BLACK);
	//		paint.setFilterBitmap(false);
	//		paint.setDither(false);
	//
	//		//startRefreshingLoop(); 
	//	}
	//	/**
	//	 * setDotsColor() choose gray (false) or colored (true) dots in the Equalizer 
	//	 * @param colored = true/false
	//	 */
	//	public void setDotsColor(boolean colored) {
	//		dot = new Bitmap[8];
	//		if (colored == true){
	//			// each dot in the meter is its own graphic due to various gradients and
	//			// alpha settings
	//			dot[0] = BitmapFactory.decodeResource(_context.getResources(),
	//				R.drawable.new_voicemessage_equalizer_active_1);
	//			dot[1] = BitmapFactory.decodeResource(_context.getResources(),
	//				R.drawable.new_voicemessage_equalizer_active_2);
	//			dot[2] = BitmapFactory.decodeResource(_context.getResources(),
	//				R.drawable.new_voicemessage_equalizer_active_3);
	//			dot[3] = BitmapFactory.decodeResource(_context.getResources(),
	//				R.drawable.new_voicemessage_equalizer_active_4);
	//			dot[4] = BitmapFactory.decodeResource(_context.getResources(),
	//				R.drawable.new_voicemessage_equalizer_active_5);
	//			dot[5] = BitmapFactory.decodeResource(_context.getResources(),
	//				R.drawable.new_voicemessage_equalizer_active_6);
	//			dot[6] = BitmapFactory.decodeResource(_context.getResources(),
	//				R.drawable.new_voicemessage_equalizer_active_7);
	//			dot[7] = BitmapFactory.decodeResource(_context.getResources(),
	//				R.drawable.new_voicemessage_equalizer_active_8);
	//		} else {
	//			for(int i = 0; i < 8; i++){
	//				dot[i] = BitmapFactory.decodeResource(_context.getResources(),
	//						R.drawable.new_voicemessage_equalizer_inactive);//dotOff;	
	//			}
	//		}
	//	}
	//
	//	public void activate() {
	//		if(isActive == false){
	//			isActive = true;
	////			startRefreshingLoop();
	//		}
	//	}
	//
	//	public void deactivate() {
	//		if(isActive){
	//			isActive = false;
	//			setDotsColor(false); // Bottom line with gray dots 
	//			// Be sure to invalidate one last time to go back to gray
	//			invalidate();
	//			stopRefreshingLoop();
	//		}
	//	}
	//
	//	public void setMeterValue(float currentMeterValue) {
	//		// Pick a random column in the meter
	//		int col = 3 + (int) (Math.random() * (NUM_BARS - 6));
	//		// Set selected and surrounding columns to incoming and
	//		// randomly-adjusted values respectively
	//		barValues[col - 3] = clamp((int) currentMeterValue - 2
	//				- ((int) (Math.random() * 2)), 1, 9);
	//		barValues[col - 2] = clamp((int) currentMeterValue - 1
	//				- ((int) (Math.random() * 2)), 1, 9);
	//		barValues[col - 1] = clamp((int) currentMeterValue
	//				- ((int) (Math.random() * 2)), 1, 9);
	//		barValues[col] = clamp((int) currentMeterValue, 1, 9);
	//		barValues[col + 1] = clamp((int) currentMeterValue
	//				- ((int) (Math.random() * 2)), 1, 9);
	//		barValues[col + 2] = clamp((int) currentMeterValue - 1
	//				- ((int) (Math.random() * 2)), 1, 9);
	//		barValues[col + 3] = clamp((int) currentMeterValue - 2
	//				- ((int) (Math.random() * 2)), 1, 9);
	//	}
	//
	//	@Override
	//	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
	//		setMeasuredDimension((int)(density * 320),(int)(density * 234));
	//	}
	//
	//	@Override
	//	protected void onDraw(Canvas canvas) {
	//		// Where the display magic happens
	//		float x = density;
	//		int y;
	//		
	//// 01.12.2010 Arcadi
	////		
	//// Fix bug: "Two rows of circles are missing. Should be 8 in total."
	////		
	//// Note: 
	////		according to Android_Final_Drop-StyleGuide.pdf : 2.6 Voice Message / Greeting, page 13
	//// 		both high and mid densities should have same number of circle rows (8)
	///*		
	//		if (density == MID_DENSITY){
	//			y = 74;
	//			maxDot = 5;
	//		}
	//		else {
	//			y = 20;
	//			maxDot = 7;
	//		}
	//*/			
	//		y = 20;
	//		int maxDot = 7 - canceledDots;
	//	
	//		paintMatrix.reset();
	//		if (isActive) {
	//			// Only draw if we have been activated
	//			for (int i = 0; i < NUM_BARS; i++) {
	//				int lvl = clamp(barValues[i], 0, maxDot);
	//				for (int d = 0; d <= lvl; d++) {
	//					paintMatrix.setScale(1.0f,1.0f,1.0f,1.0f);
	//					//paintMatrix.postTranslate(i * (x*26.66f), (x * 20) + (7 * (x*26.66f)) - (d * (x*26.66f)));
	//					paintMatrix.postTranslate(i * (x*26.66f), (x * y) + ((maxDot + canceledDots) * (x*26.66f)) - (d * (x*26.66f)));
	//					canvas.drawBitmap(dot[d], paintMatrix, paint);
	//				}
	//				if(lvl == maxDot){	/*	Commented by Yair - Bug 2243			
	//					paintMatrix.setScale(1.0f,1.0f,1.0f,1.0f);
	//					//paintMatrix.postTranslate(i * (x*26.66f), (x * 20) );
	//					paintMatrix.postTranslate(i * (x*26.66f), (x * y) * (canceledDots + 1));
	//					canvas.drawBitmap(dot[maxDot], paintMatrix, paint);		*/			
	//				}
	//				else if(lvl < maxDot ){// in case the amplitude level is less then max do padding
	//					for(int k = lvl; k < maxDot+1; k++){
	//						paintMatrix.setScale(1.0f,1.0f,1.0f,1.0f);
	//						//paintMatrix.postTranslate(i * (x*26.66f), (x * 20) + (7 * (x*26.66f)) - (k * (x*26.66f)));
	//						paintMatrix.postTranslate(i * (x*26.66f), (x * y) + ((maxDot + canceledDots) * (x*26.66f)) - (k * (x*26.66f)));
	//						canvas.drawBitmap(dotBg, paintMatrix, paint);
	//					}
	//				}
	//			}
	//		} else {
	//			for (int i = 0; i < NUM_BARS; i++) {
	//				paintMatrix.setScale(1.0f,1.0f,1.0f,1.0f);//(0.675f, 0.675f, 0.5f, 0.5f);
	//				//paintMatrix.postTranslate((i * (x*26.66f)), (x * 20) + (7 * (x*26.66f)));
	//				paintMatrix.postTranslate((i * (x*26.66f)), (x * y) + ((maxDot + canceledDots) * (x*26.66f)));
	//				if(dotOff != null) {
	//					canvas.drawBitmap(dotOff, paintMatrix, paint);
	//				}
	//				if(dotBg != null) {
	//					for(int k = 1 ; k < maxDot+1; k++)
	//					{
	//						paintMatrix.setScale(1.0f,1.0f,1.0f,1.0f);
	//						//paintMatrix.postTranslate(i * (x*26.66f), (x * 20) + (7 * (x*26.66f)) - (k * (x*26.66f)));
	//						paintMatrix.postTranslate(i * (x*26.66f), (x * y) + ((maxDot + canceledDots) * (x*26.66f)) - (k * (x*26.66f)));
	//						canvas.drawBitmap(dotBg, paintMatrix, paint);
	//					}
	//				}
	//			}
	//		}
	//	}
	//
	//	// Handler that will update values necessary to draw the effect, and call
	//	// itself
	//	// based on a desired frame rate (FRAME_RATE)
	//	private Handler refreshHandler = new Handler() {
	//		@Override
	//		public void handleMessage(Message msg) {
	//			switch (msg.what) {
	//			case REFRESH_TICK_ON:
	//				if (isActive) {
	//					for (int d = 0; d < NUM_BARS; d++) {
	//						barValues[d] -= 1;
	//						if (barValues[d] < 1) {
	//							barValues[d] = 1;
	//						}
	//					}
	//					invalidate();
	//				}
	//				refreshHandler.sendEmptyMessageDelayed(REFRESH_TICK_ON, 1000/ FRAME_RATE);
	//				break;
	//			case REFRESH_TICK_OFF:
	//				break;
	//			case REFRESH_TICK_KILL:			
	//				// handle last call
	//				refreshHandler.removeMessages(REFRESH_TICK_ON);
	//				refreshHandler.removeMessages(REFRESH_TICK_OFF);
	//				break;
	//			}
	//		}
	//	};
	//	/**
	//	 * startRefreshingLoop() start refreshing loop by sending the first message
	//	 */
	//	public void startRefreshingLoop(){
	//		this.refreshHandler.sendEmptyMessage(REFRESH_TICK_ON);
	//	}
	//	/**
	//	 * stopRefreshingLoop() stop the refreshing loop 
	//	 */
	//	public void stopRefreshingLoop(){
	//		this.refreshHandler.sendEmptyMessage(REFRESH_TICK_OFF);
	//	}	
	//	/**
	//	 * killRefreshingLoop() terminate refreshing loop by sending termination message 
	//	 */
	//	public void killRefreshingLoop(){
	//		this.refreshHandler.sendEmptyMessage(REFRESH_TICK_KILL);
	//	}
	//	
	//	protected void onDetachedFromWindow() {
	//		this.killRefreshingLoop();
	//	};
	//	
	//	// Helper function
	//	private int clamp(int val, int low, int high) {
	//		if (val < low) {
	//			return low;
	//		} else if (val > high) {
	//			return high;
	//		} else {
	//			return val;
	//		}
	//	}
	//	public void setDensity(float d){
	//		if (d == HIGH_DENSITY){
	//			this.density = HIGH_DENSITY;
	//		}
	//		else{
	//			this.density = MID_DENSITY;
	//		}
	//	}
	//	
	//	
	//	/* Bug 2243
	//	 * In case there is not enough space to display the full number of rows
	//	 * cancel the needed numbers of dots.
	//	 * To be used by the activity in which the view is used.
	//	 */
	//	public void cancelDots(int cancelDotsNum)
	//	{
	//		/*
	//		 * The cancel dots method is relevant just for low density
	//		 * In HD - the problem does not exist
	//		 */
	//		if (this.density == HIGH_DENSITY)
	//		{
	//			return;
	//		}
	//		if (cancelDotsNum <= 0)
	//		{
	//			canceledDots = 0;
	//		}
	//		else if (cancelDotsNum > 3)
	//		{
	//			canceledDots = 3;
	//		}
	//		else
	//		{
	//			canceledDots = cancelDotsNum;
	//		}
	//		this.invalidate();
	//	}
}
