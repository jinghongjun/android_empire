package com.empire.library.communication.protocol.http;

import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import com.alibaba.fastjson.JSONObject;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.RetryPolicy;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.facebook.stetho.okhttp.StethoInterceptor;
import com.peachvalley.utils.JSonUtils;
import com.squareup.okhttp.OkHttpClient;
import com.yht.R;
import com.yht.event.TokenExpiredEvent;
import com.yht.util.Logger;
import com.yht.util.SystemFunction;
import com.yht.util.Utils;

import java.util.HashMap;
import java.util.Map;

import de.greenrobot.event.EventBus;

/// SA: See Also
/// 1. 使用OkHttp作为Volley的传输 http://blog.csdn.net/lonewolf521125/article/details/47256475
/// 2. Github project: https://github.com/facebook/stetho
/// 3. Android调试神器Stetho(Facebook出品)的使用 http://www.it165.net/pro/html/201505/39734.html
/// 4. 仅作为Android 调试模式工具的Stetho http://wiki.jikexueyuan.com/project/android-weekly/issue-146/stetho-android-debug-builds-only.html
///
public abstract class HttpRequestUtil implements OnCancelListener {
	private static final String TAG = HttpRequestUtil.class.getSimpleName();

	/**没有网络*/
	public static final int REQUEST_CODE_NO_NETWORK = 1000;
	/**其他错误*/
	public static final int REQUEST_CODE_ERROR = 1001;
	/**服务端返回数据错误*/
	public static final int REQUEST_CODE_ERROR_SERVER = 1002;
	
	public static final int REQUEST_CODE_VOLLEY_ERROR_CODE = -1000;
	
	private static final String APP_KEY = "app_key";
	public static final String APP_KEY_VALUE = "taogu";
	public static final String APP_SECRET = "elu$jdo#mp";
	private static final String FIELD_METHOD = "method";

	public static final String TOKEN = "token";
	private static final String APP_VERSION = "version";
	private static final String DEVICE_ID = "uuid";
	private static final String OS_NAME = "os";
	private static final String OS_VERSION = "osversion";
	private static final String NETWORKING = "net";
	private static final String RESOLUTION = "resolution";
	private static final String MODEL = "model";

	protected static final String METHOD_FEEDBACK = "createFeedback";

	public static final String APP_IDENTIFIER = "app";
	private String mAppIdentifier;
	private Context mContext;
	private HttpRequestListener mListener;
	protected HttpRequestUtil(Context ctx) {
		this.mContext = null == ctx ? ctx : ctx.getApplicationContext();
		this.mListener = null;

		if (null == ctx) {
			// just make runnable for ut compatible, it should never be present in real world.
			createHttpResponseHandler("NULL");
			mAppIdentifier = "UNKNOWN";
		} else {
			createHttpResponseHandler(ctx.getClass().getSimpleName());
			mAppIdentifier = SystemFunction.parseAppIdentifier(ctx, APP_IDENTIFIER);
		}
	}

	protected void setRequestCallBack(HttpRequestListener listener) {
		this.mListener = listener;
	}

	/**
	 * 添加请求参数
	 * @param key
	 * @param value
	 */
	protected void put(String key, String value) {
		if (mParams == null) {
			createRequestParam();
		}
		mParams.put(key, value);
	}

	/**
	 * 删除请求参数
	 * @param key
	 */
	protected void remove(String key) {
		if (mParams != null) {
			mParams.remove(key);
		}
	}

	private void createRequestParam() {
		if (mParams == null) {
			mParams = new HashMap<String, String>();
			mParams.put(APP_KEY, APP_KEY_VALUE);
			mParams.put(APP_IDENTIFIER, mAppIdentifier);
		}
	}

	private String getUrl(String method) {
		String url = getBaseUrl();
		if (!TextUtils.isEmpty(url)) {
			url += "?" + getMethodKey() + "=" + method;
			return url;
		}
		return null;
	}

    /**
	 * 发起请求前操作
	 */
	private void prepareRequest() {
		createRequestParam();

		isRequestCancel = false;
		cancelCurRequest();
	}
	
	private HashMap<String, String> generateToken(String paramToken){
		mParams.remove(TOKEN);
		if(TextUtils.isEmpty(paramToken)) {
			StringBuffer sb = new StringBuffer();
			sb.append(APP_KEY_VALUE);
			sb.append(APP_SECRET);
			String urlparam = sb.toString();
			String token = Utils.MD5(urlparam);
			mParams.put(TOKEN, token.toUpperCase());
			Log.d(TAG, "---string(" + token.length() + "):" + token.toUpperCase());
		} else {
			mParams.put(TOKEN, paramToken);
		}

		mParams.put(APP_VERSION, getAppVersion());
		mParams.put(DEVICE_ID, SystemFunction.getDeviceId(mContext));
		mParams.put(OS_NAME, getOsName());
		mParams.put(OS_VERSION, getOsVersion());
		mParams.put(NETWORKING, getNetworking());
		mParams.put(RESOLUTION, getResolution());
		mParams.put(MODEL, getModel());
		
		return mParams;
	}

	/**
	 * 发起异步(POST)请求
	 * @return
	 */
	protected void doAsyncRequestPost(String method) {
		doAsyncRequestPost(method, null);
	}
	/**
	 * 发起异步(POST)请求
	 * @return
	 */
	protected void doAsyncRequestPost(String method, String token) {
		doAsyncRequestPost(method, token, null);
	}

	public void doAsyncRequestPost(String method, String token,
								   HttpRequestListener httpRequestListener) {
		if (null != httpRequestListener) {
			setRequestCallBack(httpRequestListener);
		}

		prepareRequest();
		if (isNetworkAvailable()) {
			generateToken(token);
			if (METHOD_FEEDBACK.equals(method)) {
				put(APP_VERSION, Utils.getVersion(mContext));
			}
			addToRequestQueue(Request.Method.POST, getUrl(method), mParams);
		} else {
			if (!isRequestCancel) {
				handleFailure(REQUEST_CODE_NO_NETWORK, null);
			}
		}
	}

	/**
	 * http请求成功回调
	 * @param result
	 */
	private void handleSuccess(String result) {
		try {
			if (hasResponseResult(result)) {
				if (isResponseOkay(result)) {
					if (hasResponseData(result)) {
						if (mListener != null) {
							mListener.onSuccess(result);
						}
					} else {
						handleFailure(REQUEST_CODE_ERROR, mContext.getString(R.string.tip_network_error));
					}
				} else {
                    if (isTokenExpired(result)){

						EventBus.getDefault().post(new TokenExpiredEvent(result));

                    }else{
                        handleFailure(getErrorCode(result), getErrorMessage(result));
                    }
				}
			} else {
				handleFailure(REQUEST_CODE_ERROR_SERVER, result);
			}
		} catch (Exception e) {
			handleFailure(REQUEST_CODE_ERROR_SERVER, result);
		}
	}

	public static final String RES_KEY_DATA = "data";
	public static final String RES_KEY_CODE = "code";
	public static final String RES_KEY_CODE_DESC = "codeDesc";
	public static final String RES_KEY_CODE_DESC_USER = "codeDescUser";
	public static final String RES_KEY_RESULT_FLAG = "result_flag";
    public static final String RES_KEY_RESULT_TXT = "result_txt";
    public static final String RES_KEY_PROMPT_ARRAY = "prompt_array";
	public static final String RES_KEY_IS_CLOCK = "isClock";

	protected boolean hasResponseResult(String result) {
		return !TextUtils.isEmpty(result) && result.contains(RES_KEY_CODE);
	}

	public boolean isResponseOkay(String result) {
		JSONObject object = JSonUtils.parseObjectWithoutException(result);
		if (null != object) {
			return 0 == object.getIntValue(RES_KEY_CODE) || object.containsKey(RES_KEY_DATA);
		}
		return false;
	}
	protected boolean hasResponseData(String result) {
		JSONObject object = JSonUtils.parseObjectWithoutException(result);
		if (null != object) {
			return object.containsKey(RES_KEY_DATA) ||
					0 == object.getInteger(RES_KEY_CODE);
		}

		return false;
	}

	public boolean isTokenExpired(String result) {
		JSONObject object = JSonUtils.parseObjectWithoutException(result);
		if (null != object) {
			return 401 == object.getIntValue(RES_KEY_CODE);
		}
		return false;
	}

	protected String getErrorMessage(String result) {
		if (!TextUtils.isEmpty(result)) {
			JSONObject object = JSonUtils.parseObjectWithoutException(result);
			if (null != object) {
				return object.getString(RES_KEY_CODE_DESC_USER);
			}
		}

		return "UNKNOWN ERROR.";
	}


	protected int getErrorCode(String result) {
		if (!TextUtils.isEmpty(result)) {
			JSONObject object = JSonUtils.parseObjectWithoutException(result);
			if (null != object) {
				return object.getInteger(RES_KEY_CODE);
			}
		}

		return -1;
	}

	protected void handleFailure(int code, String arg1) {
		Logger.e("code:" + code + ", message:" + arg1);
		String msg = arg1;
		switch (code) {
		case REQUEST_CODE_NO_NETWORK:
			if (mContext != null) {				
				msg = mContext.getString(R.string.tip_no_network);
			}
			break;
		case REQUEST_CODE_ERROR_SERVER:
			msg = mContext.getString(R.string.tip_server_data_error);
			break;
		case REQUEST_CODE_VOLLEY_ERROR_CODE:
			msg = mContext.getString(R.string.tip_volley_error);
			break;
		}
		if (mListener != null) {
			if(!mListener.onFailure(code, msg)) {
				Utils.showMsg(mContext, msg);
			}
		}
	}

	public void onCancel(DialogInterface dialog) {
		cancelCurRequest();
		isRequestCancel = true;
	}

	public void cancelCurRequest() {
		cancelPendingRequests(mRequestTag);
	}

    protected String getMethodKey() {
        return FIELD_METHOD;
    }

    /**
	 * 获取请求的基础URL
	 * @return
	 */
	public abstract String getBaseUrl();

	private static String appVersion;
	private String getAppVersion() {
		if (null == appVersion) {
			appVersion = String.valueOf(Utils.getVersionCode(mContext));
		}

		return appVersion;
	}

	private String getOsName() {
		return "ANDROID";
	}

	private String getOsVersion() {
		return String.valueOf(Build.VERSION.SDK_INT);
	}

	private String getNetworking() {
		if (mContext != null) {
			ConnectivityManager mConnectivityManager = (ConnectivityManager) mContext.
					getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo mWiFiNetworkInfo = mConnectivityManager
					.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
			if (mWiFiNetworkInfo != null) {
				return "TYPE_WIFI";
			}
		}

		return "TYPE_MOBILE";
	}

	private String getResolution() {
		DisplayMetrics dm = new DisplayMetrics();

		WindowManager manager = (WindowManager) mContext
				.getSystemService(Context.WINDOW_SERVICE);
		manager.getDefaultDisplay().getMetrics(dm);

		int screenWidth = dm.widthPixels;
		int screenHeight = dm.heightPixels;
		return screenWidth + "x" + screenHeight;
	}

	private String getModel() {
		return Build.MODEL;
	}


	private boolean isRequestCancel = false;
	private HashMap<String, String> mParams;

	private String mRequestTag;
	protected Response.Listener<String> mJsonResponseHandler;
	protected Response.Listener<org.json.JSONObject> mJsonObjectResponseHandler;
	protected Response.ErrorListener mErrorResponseHandler;

	protected void setRequestTag(String tag) {
		Log.v(TAG, "setRequestTag change tag from " + mRequestTag + " to " + tag);
		mRequestTag = tag;
	}

	private void createHttpResponseHandler(String tag) {
		mRequestTag = tag;

		mJsonResponseHandler = new Response.Listener<String>() {
			@Override
			public void onResponse(String response) {
				if (!isRequestCancel && mContext != null) {
					Log.d(TAG, "----SUCCESS RESPONSE:" + response);
					handleSuccess(response);
				}
			}
		};
		
		mJsonObjectResponseHandler =  new Response.Listener<org.json.JSONObject>() {
            @Override
            public void onResponse(org.json.JSONObject response) {
                if (!isRequestCancel && mContext != null) {
                    Log.d(TAG, "----SUCCESS RESPONSE:" + response.toString());
                    handleSuccess(response.toString());
                }
            }
        };

		mErrorResponseHandler = new Response.ErrorListener() {
			@Override
			public void onErrorResponse(VolleyError error) {
				VolleyLog.d(TAG, "Error: " + error.getMessage());
				if (!isRequestCancel && mContext != null) {
					Log.d(TAG, "----FAILD RESPONSE:" + error.toString());
					handleFailure(REQUEST_CODE_VOLLEY_ERROR_CODE, error.toString());
				}
			}
		};
	}

	private static RequestQueue mRequestQueue;
	private RequestQueue getRequestQueue() {
		if (mRequestQueue == null) {
			OkHttpClient client = new OkHttpClient();
			client.networkInterceptors().add(new StethoInterceptor());
			mRequestQueue = Volley.newRequestQueue(mContext.getApplicationContext(), new OkHttpStack(client));
		}

		return mRequestQueue;
	}

	public <T> void addToRequestQueue(int httpMethod, String url, final HashMap<String, String> paramMap) {
		// set the default tag if tag is empty
		Log.v(TAG, "addToRequestQueue, url = " + url + ", paraMap = " + paramMap + ", tag = " + mRequestTag);
		StringRequest req = new StringRequest(httpMethod, url,
				mJsonResponseHandler, mErrorResponseHandler) {
			@Override
			protected Map<String, String> getParams() {
				return paramMap;
			}
		};

		addToRequestQueue(req);
	}

	public <T> void addToRequestQueue(Request<T> req) {
		Log.v(TAG, "addToRequestQueue, url = " + req.getUrl() + ", tag = " + mRequestTag);
		req.setTag(TextUtils.isEmpty(mRequestTag) ? TAG : mRequestTag);
		RetryPolicy policy = req.getRetryPolicy();
		Log.v(TAG, "addToRequestQueue, retry policy: try " + policy.getCurrentRetryCount() +
				" with timeout " + policy.getCurrentTimeout() + " before set to " + DEFAULT_RETRY_POLICY.toString());
		req.setRetryPolicy(DEFAULT_RETRY_POLICY);
		getRequestQueue().add(req);
	}

	private static DefaultRetryPolicy DEFAULT_RETRY_POLICY;
	static {
		DEFAULT_RETRY_POLICY = new DefaultRetryPolicy(20 * 1000, 1, 1.0f);
	}

	public static void cancelPendingRequests(Context context) {
		cancelPendingRequests(context.getClass().getSimpleName());
	}

	public static void cancelPendingRequests(Object tag) {
		if (mRequestQueue != null && null != tag) {
			Log.d(TAG, "cancelPendingRequests, cancel all request with tag = " + tag);
			mRequestQueue.cancelAll(tag);
		}
	}

	private static final String EXTRA_URL_ENTERTAINMENT = "http://www.91yimeng.cn/hoswifi/index-wifi.php";
	public void checkEntertainmentEnv(HttpRequestListener httpRequestListener) {
		setRequestCallBack(httpRequestListener);
		prepareRequest();
		if (isNetworkAvailable()) {
			addToRequestQueue(Request.Method.POST, EXTRA_URL_ENTERTAINMENT, generateToken(""));
		} else {
			if (!isRequestCancel) {
				handleFailure(REQUEST_CODE_NO_NETWORK, null);
			}
		}
	}

	protected boolean isNetworkAvailable() {
		return Utils.isNetworkAvailable(mContext);
	}
}
