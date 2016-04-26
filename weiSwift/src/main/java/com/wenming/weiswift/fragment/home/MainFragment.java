package com.wenming.weiswift.fragment.home;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import com.nostra13.universalimageloader.core.ImageLoader;
import com.sina.weibo.sdk.auth.AuthInfo;
import com.sina.weibo.sdk.auth.Oauth2AccessToken;
import com.sina.weibo.sdk.auth.WeiboAuthListener;
import com.sina.weibo.sdk.auth.sso.SsoHandler;
import com.sina.weibo.sdk.exception.WeiboException;
import com.sina.weibo.sdk.net.RequestListener;
import com.sina.weibo.sdk.openapi.StatusesAPI;
import com.sina.weibo.sdk.openapi.UsersAPI;
import com.sina.weibo.sdk.openapi.models.ErrorInfo;
import com.sina.weibo.sdk.openapi.models.Status;
import com.sina.weibo.sdk.openapi.models.StatusList;
import com.sina.weibo.sdk.openapi.models.User;
import com.wenming.weiswift.NewFeature;
import com.wenming.weiswift.R;
import com.wenming.weiswift.common.login.AccessTokenKeeper;
import com.wenming.weiswift.common.login.Constants;
import com.wenming.weiswift.common.util.LogUtil;
import com.wenming.weiswift.common.util.NetUtil;
import com.wenming.weiswift.common.util.SDCardUtil;
import com.wenming.weiswift.common.util.SharedPreferencesUtil;
import com.wenming.weiswift.common.util.ToastUtil;
import com.wenming.weiswift.fragment.home.weiboitem.WeiboAdapter;
import com.wenming.weiswift.fragment.home.weiboitem.WeiboItemSapce;

import java.util.ArrayList;

/**
 * Created by wenmingvs on 15/12/26.
 */
public class MainFragment extends Fragment {

    private AuthInfo mAuthInfo;
    private Oauth2AccessToken mAccessToken;
    private SsoHandler mSsoHandler;
    private Context mContext;
    private RecyclerView mRecyclerView;
    private WeiboAdapter mAdapter;
    private LinearLayoutManager mLayoutManager;
    private Activity mActivity;
    private View mToolBar;
    private TextView mLogin;
    private TextView mRegister;
    private TextView mUserName;
    private View mView;
    private ArrayList<Status> mDatas;
    private StatusesAPI mStatusesAPI;
    private boolean mFirstLoad;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private ArrayList<Status> mWeiBoCache;
    private int mLastVisibleItemPositon;//因为有search bar的存在，所以是微博条目是从1开始计数
    private long lastWeiboID;
    private UsersAPI mUsersAPI;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        LogUtil.d("onAttach");
        mFirstLoad = true;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        LogUtil.d("onCreateView");
        mActivity = getActivity();
        mContext = getActivity();
        initAccessToken();
        if (NewFeature.LOGIN_STATUS == true) {
            mView = inflater.inflate(R.layout.mainfragment_layout, container, false);
            initLoginStateTitleBar();
            initRecyclerView();
            initRefreshLayout();
            return mView;
        } else {
            mView = inflater.inflate(R.layout.mainfragment_unlogin_layout, container, false);
            initunLoginStateTitleBar();
            return mView;
        }
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        LogUtil.d("onViewCreated");
        super.onViewCreated(view, savedInstanceState);
        if (NewFeature.LOGIN_STATUS == true) {
            mToolBar.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        LogUtil.d("onResume in MainFragment");
    }

    @Override
    public void onDestroyView() {
        LogUtil.d("onDestroyView");
        super.onDestroyView();
        if (NewFeature.LOGIN_STATUS == true) {
            mToolBar.setVisibility(View.GONE);
            mFirstLoad = false;
        }

    }


    public void hideToolBar() {
        mToolBar.setVisibility(View.GONE);
    }

    public void showToolBar() {
        mToolBar.setVisibility(View.VISIBLE);
    }

    /**
     * 发起 SSO 登陆的 Activity 必须重写 onActivityResults
     *
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        LogUtil.d("onActivityResult");
        super.onActivityResult(requestCode, resultCode, data);
        if (mSsoHandler != null) {
            mSsoHandler.authorizeCallBack(requestCode, resultCode, data);
        }

    }

    @Override
    public void onHiddenChanged(boolean hidden) {

        super.onHiddenChanged(hidden);
        if (hidden) {
            hideToolBar();
        } else {
            showToolBar();
        }
    }

    private void initAccessToken() {
        mAuthInfo = new AuthInfo(mContext, Constants.APP_KEY,
                Constants.REDIRECT_URL, Constants.SCOPE);
        mSsoHandler = new SsoHandler(mActivity, mAuthInfo);
        mAccessToken = AccessTokenKeeper.readAccessToken(mContext);
        mStatusesAPI = new StatusesAPI(mContext, Constants.APP_KEY, mAccessToken);
        mUsersAPI = new UsersAPI(mContext, Constants.APP_KEY, mAccessToken);
    }

    private void initLoginStateTitleBar() {
        mActivity.getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.toolbar_home_login);
        mToolBar = mActivity.findViewById(R.id.toolbar_home_login);
        mUserName = (TextView) mToolBar.findViewById(R.id.toolbar_username);
        refreshUserName();
    }

    private void refreshUserName() {
        long uid = Long.parseLong(mAccessToken.getUid());
        mUsersAPI.show(uid, new RequestListener() {
            @Override
            public void onComplete(String response) {
                // 调用 User#parse 将JSON串解析成User对象
                User user = User.parse(response);
                if (user != null) {
                    mUserName.setText(user.name);
                }
            }

            @Override
            public void onWeiboException(WeiboException e) {
                ErrorInfo info = ErrorInfo.parse(e.getMessage());
                ToastUtil.showShort(mContext, info.toString());
            }
        });
    }


    private void initunLoginStateTitleBar() {
        mActivity.getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.toolbar_home_unlogin);
        mToolBar = mActivity.findViewById(R.id.toolbar_home_unlogin);
        mLogin = (TextView) mToolBar.findViewById(R.id.login);
        mRegister = (TextView) mToolBar.findViewById(R.id.register);
        mLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSsoHandler.authorizeWeb(new AuthListener());
            }
        });

        mRegister.setOnClickListener(new View.OnClickListener() {
            /**
             * @param view
             */
            @Override
            public void onClick(View view) {
                mSsoHandler.registerOrLoginByMobile("验证码登陆", new AuthListener());
            }
        });
    }

    /**
     * 初始化下拉刷新控件
     * 1. 设置下拉刷新执行的逻辑
     * 2. 第一次进来就自动下拉刷新
     */
    private void initRefreshLayout() {
        mSwipeRefreshLayout = (SwipeRefreshLayout) mView.findViewById(R.id.swipe_refresh_widget);
        mSwipeRefreshLayout.setColorSchemeResources(android.R.color.holo_blue_bright,
                android.R.color.holo_green_light, android.R.color.holo_orange_light,
                android.R.color.holo_red_light);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                pullToRefreshData();
            }
        });
        mSwipeRefreshLayout.post(new Runnable() {
            @Override
            public void run() {
                mSwipeRefreshLayout.setRefreshing(true);
                pullToRefreshData();
            }
        });

    }


    private void initRecyclerView() {
        mRecyclerView = (RecyclerView) mView.findViewById(R.id.weiboRecyclerView);
        if (mFirstLoad == true) {
            mRecyclerView.addItemDecoration(new WeiboItemSapce((int) mContext.getResources().getDimension(R.dimen.home_weiboitem_space)));
        }
        mRecyclerView.setHasFixedSize(true);
        mLayoutManager = new LinearLayoutManager(mContext);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mDatas = new ArrayList<Status>();
        mWeiBoCache = new ArrayList<Status>();
        mAdapter = new WeiboAdapter(mDatas, mContext);
        mRecyclerView.setAdapter(mAdapter);


        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {

                switch (newState) {
                    case RecyclerView.SCROLL_STATE_DRAGGING:
                        ImageLoader.getInstance().pause();
                        break;
                    case RecyclerView.SCROLL_STATE_IDLE:
                        ImageLoader.getInstance().resume();
                        break;
                }
                if (newState == RecyclerView.SCROLL_STATE_IDLE && mLastVisibleItemPositon + 1 == mAdapter.getItemCount() && !mSwipeRefreshLayout.isRefreshing()) {
                    if (mDatas.size() - 1 < mWeiBoCache.size() && mDatas.size() != 0) {//读取本地缓存数据
                        addDataFromCache(mLastVisibleItemPositon - 1);
                        mAdapter.setData(mDatas);
                        mAdapter.notifyDataSetChanged();
                    } else {
                        lastWeiboID = Long.parseLong(mDatas.get(mDatas.size() - 1).id);
                        ToastUtil.showShort(mContext, "本地数据已经被读取完，开始进行网络请求");
                        pullToLoadMoreDataFromURL();
                    }
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                mLastVisibleItemPositon = mLayoutManager.findLastVisibleItemPosition();
                //LogUtil.d("mLastVisibleItemPositon = " + mLastVisibleItemPositon);
            }
        });
    }

    /**
     * 滑动到底部的时候，下拉加载更多的逻辑，使用帧动画
     */
    private void pullToLoadMoreDataFromURL() {
        if (NetUtil.isConnected(mContext)) {
            if (mAccessToken != null && mAccessToken.isSessionValid()) {
                mStatusesAPI.friendsTimeline(0, lastWeiboID, NewFeature.GET_WEIBO_NUMS, 1, false, NewFeature.WEIBO_TYPE, false,
                        new RequestListener() {
                            @Override
                            public void onComplete(String response) {
                                ArrayList<Status> status = StatusList.parse(response).statusList;
                                if (status.size() > 0) {
                                    status.remove(0);
                                }
                                mWeiBoCache.addAll(status);
                                addDataFromCache(mLastVisibleItemPositon - 1);
                                mAdapter.setData(mDatas);
                                mAdapter.notifyDataSetChanged();
                            }

                            @Override
                            public void onWeiboException(WeiboException e) {
                                ToastUtil.showShort(mContext, "服务器出现问题！");
                            }
                        });
            }
        } else {
            ToastUtil.showShort(mContext, "网络请求失败，没有网络");
        }
    }

    private void saveJsonData(Context context, String jsonString) {
        if (NewFeature.SAVE_TO_SDCARD) {
            SDCardUtil.put(mContext, SDCardUtil.getSDCardPath() + "/weiSwift/", "json.txt", jsonString);
        } else {
            SharedPreferencesUtil.put(mContext, "wenming", jsonString);
        }
    }

    private String getJsonData(Context context) {
        String response;
        if (NewFeature.SAVE_TO_SDCARD) {
            response = SDCardUtil.get(mContext, SDCardUtil.getSDCardPath() + "/aaa/", "json.txt");
        } else {
            response = (String) SharedPreferencesUtil.get(mContext, "wenming", new String());
        }
        return response;
    }

    /**
     * 下拉刷新执行的逻辑(String) SharedPreferencesUtil.ge
     * 1. 检查网络是否可用，如果网络不可用，则读取本地缓存，并且关闭圆圈动画
     * 2. 网络可用，则请求数据，并且设置回调，回调执行完成后关闭圆圈动画，如果请求失败，也要关闭动画
     */
    private void pullToRefreshData() {
        if (NetUtil.isConnected(mContext)) {
            if (mAccessToken != null && mAccessToken.isSessionValid()) {
                mStatusesAPI.friendsTimeline(0, 0, NewFeature.GET_WEIBO_NUMS, 1, false, NewFeature.WEIBO_TYPE, false,
                        new RequestListener() {
                            @Override
                            public void onComplete(String response) {
                                //短时间内疯狂请求数据，服务器会返回数据，但是是空数据。为了防止这种情况出现，要在这里要判空
                                if (!TextUtils.isEmpty(response)) {
                                    saveJsonData(mContext, response);
                                    getWeiBoCache(0);
                                } else {
                                    ToastUtil.showShort(mContext, "网络请求太快，服务器返回空数据，请注意请求频率");
                                }
                                mSwipeRefreshLayout.setRefreshing(false);
                            }

                            @Override
                            public void onWeiboException(WeiboException e) {
                                ErrorInfo info = ErrorInfo.parse(e.getMessage());
                                ToastUtil.showShort(mContext, info.toString());
                                mSwipeRefreshLayout.setRefreshing(false);
                            }
                        });
            }
        } else {
            getWeiBoCache(0);
            ToastUtil.showShort(mContext, "没有网络,读取本地缓存");
            mSwipeRefreshLayout.setRefreshing(false);
        }
    }

    /**
     * 从本地缓存中拿到数据并且解析到mWeiBoCache
     *
     * @param start
     */
    private void getWeiBoCache(int start) {
        mWeiBoCache.clear();
        mDatas.clear();
        String response = getJsonData(mContext);
        if (response.startsWith("{\"statuses\"")) {
            mWeiBoCache = StatusList.parse(response).statusList;
            mDatas.add(0, new Status());
            addDataFromCache(0);
        }
        mAdapter.setData(mDatas);
        mAdapter.notifyDataSetChanged();
    }

    /**
     * @param start mWeiBoCache start to add
     */
    private void addDataFromCache(int start) {
        int count = 0;
        for (int i = start; i < mWeiBoCache.size(); i++) {
            if (start == mWeiBoCache.size()) {
                ToastUtil.showShort(mContext, "本地缓存已经读取完！");
                break;
            }
            if (count == NewFeature.LOAD_WEIBO_ITEM) {
                break;
            }
            mDatas.add(mWeiBoCache.get(i));
            count++;
        }
    }

    class AuthListener implements WeiboAuthListener {
        @Override
        public void onComplete(Bundle values) {
            mAccessToken = Oauth2AccessToken.parseAccessToken(values);// 从 Bundle 中解析 Token
            if (mAccessToken.isSessionValid()) {
                AccessTokenKeeper.writeAccessToken(mContext,
                        mAccessToken);//保存Token
                Toast.makeText(mContext, "授权成功,请重启App", Toast.LENGTH_SHORT)
                        .show();
            } else {
                Toast.makeText(mContext, "授权失败", Toast.LENGTH_SHORT)
                        .show();
            }

        }

        @Override
        public void onWeiboException(WeiboException e) {
            Toast.makeText(mContext,
                    "Auth exception : " + e.getMessage(), Toast.LENGTH_LONG)
                    .show();
        }

        @Override
        public void onCancel() {
            Toast.makeText(mContext, "取消授权",
                    Toast.LENGTH_LONG).show();
        }
    }
}