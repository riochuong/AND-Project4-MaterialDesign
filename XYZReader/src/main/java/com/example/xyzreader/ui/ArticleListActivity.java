package com.example.xyzreader.ui;

import android.app.ActivityOptions;
import android.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.app.SharedElementCallback;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;
import com.example.xyzreader.data.ItemsContract;
import com.example.xyzreader.data.UpdaterService;
import com.example.xyzreader.remote.NetworkUtil;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import timber.log.Timber;

/**
 * An activity representing a list of Articles. This activity has different presentations for
 * handset and tablet-size devices. On handsets, the activity presents a list of items, which when
 * touched, lead to a {@link ArticleDetailActivity} representing item details. On tablets, the
 * activity presents a grid of items as cards.
 */
public class ArticleListActivity extends AppCompatActivity implements
        LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = ArticleListActivity.class.toString();
    private static final int LOADER_ID = 0;
    public static final String EXIT_POS_STR = "exit_pos";
    private static final String URI_DATA = "uri_data";
    @BindView(R.id.toolbar)
    Toolbar mToolbar;
    @BindView(R.id.swipe_refresh_layout)
    SwipeRefreshLayout mSwipeRefreshLayout;
    @BindView(R.id.recycler_view)
    RecyclerView mRecyclerView;

    @BindView(R.id.root_layout)
    CoordinatorLayout mRootLayout;

    @BindView(R.id.empty_view)
    TextView emptyView;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss");
    // Use default locale format
    private SimpleDateFormat outputFormat = new SimpleDateFormat();
    // Most time functions can only handle 1902 - 2037
    private GregorianCalendar START_OF_EPOCH = new GregorianCalendar(2,1,1);
    // transition enter position
    private int enterPosition;
    private int exitPosition;
    private static final String VH_TAG  = "vh_tag";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_article_list);
        ButterKnife.bind(this);
        // add swipe refresh listener
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                Timber.d("Refresh Layout. Force loader to reload");
                // force loader reload
                if (! mIsRefreshing){
                    refresh();
                }
            }

        });
        getLoaderManager().initLoader(LOADER_ID, null, this);
    }

    private void displaySnackBarError(String error){
        Snackbar.make(mRootLayout, error,Snackbar.LENGTH_LONG).show();
    }

    private void refresh() {
        if (!NetworkUtil.isConnectToNetWork(this)){
            mIsRefreshing = false;
            mSwipeRefreshLayout.setRefreshing(mIsRefreshing);
            displaySnackBarError(getString(R.string.no_network_error));
            return;
        }
        startService(new Intent(this, UpdaterService.class));
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(mRefreshingReceiver,
                new IntentFilter(UpdaterService.BROADCAST_ACTION_STATE_CHANGE));
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mRefreshingReceiver);
    }

    private boolean mIsRefreshing = false;

    private BroadcastReceiver mRefreshingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (UpdaterService.BROADCAST_ACTION_STATE_CHANGE.equals(intent.getAction())) {
                mIsRefreshing = intent.getBooleanExtra(UpdaterService.EXTRA_REFRESHING, false);
                updateRefreshingUI();
                if (! NetworkUtil.isConnectToNetWork(ArticleListActivity.this)){
                    displaySnackBarError(getString(R.string.no_network_error));
                }
            }
        }
    };

    private void updateRefreshingUI() {
        mSwipeRefreshLayout.setRefreshing(mIsRefreshing);
    }

    /**
     * Finish refreshing layout
     */
    private void stopRefreshingUI(){
        mIsRefreshing = false;
        mSwipeRefreshLayout.setRefreshing(mIsRefreshing);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        Timber.d("Start loading all articles");
        return ArticleLoader.newAllArticlesInstance(this);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        // stop refreshing layout here
        if (cursor == null || cursor.getCount() == 0){
            mRecyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        }
        else{
            mRecyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            Adapter adapter = new Adapter(cursor);
            adapter.setHasStableIds(true);
            mRecyclerView.setAdapter(adapter);
            int columnCount = getResources().getInteger(R.integer.list_column_count);
            StaggeredGridLayoutManager sglm =
                    new StaggeredGridLayoutManager(columnCount, StaggeredGridLayoutManager.VERTICAL);
            mRecyclerView.setLayoutManager(sglm);
            stopRefreshingUI();
        }
    }



    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mRecyclerView.setAdapter(null);
    }


    private void setCallBack (final int enterPosition){
        this.enterPosition = enterPosition;
        setExitSharedElementCallback(new SharedElementCallback() {
            // @Override
            public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
                if (exitPosition != enterPosition && names.size() > 0) {
                    View view = mRecyclerView.findViewWithTag(exitPosition+"");
                    //view might get recycle by recylerview. In that case we cannot do much
                    if (view != null){
                        names.clear();
                        sharedElements.clear();
                        names.add(view.getTransitionName());
                        sharedElements.put(view.getTransitionName(), view);
                    }

                }
                setExitSharedElementCallback((SharedElementCallback) null);
            }
        });
    }

    @Override
    public void onActivityReenter(int resultCode, Intent data) {
        super.onActivityReenter(resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            exitPosition = data.getIntExtra(EXIT_POS_STR, enterPosition);
        }
    }

    private class Adapter extends RecyclerView.Adapter<ViewHolder> {
        private Cursor mCursor;
        private int lastAnimatedPosition = -1;
        private static final int ANIMATION_DUR = 700;
        private static final int CONSEC_DELAY = 300;

        public Adapter(Cursor cursor) {
            mCursor = cursor;
        }

        @Override
        public long getItemId(int position) {
            mCursor.moveToPosition(position);
            return mCursor.getLong(ArticleLoader.Query._ID);
        }

        private void runEnterAnimation(View view, int position) {
            if (mCursor.getCount() <= 0 || position >= getItemCount()) {
                return;
            }

            if (position > lastAnimatedPosition) {
                lastAnimatedPosition = position;
                float height = Resources.getSystem().getDisplayMetrics().heightPixels;
                view.setTranslationY(height);
                view.animate()
                        .translationY(0)
                        .setInterpolator(new DecelerateInterpolator(3.f))
                        .setDuration(ANIMATION_DUR + position * CONSEC_DELAY)
                        .start();
            }
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.list_item_article, parent, false);
            final ViewHolder vh = new ViewHolder(view);
            View cardView = view.findViewById(R.id.card_view);
            cardView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(Intent.ACTION_VIEW,
                                    ItemsContract.Items.buildItemUri(getItemId(vh.getAdapterPosition())));
                    Intent startAct = new Intent(ArticleListActivity.this, ArticleDetailActivity.class);
                    startAct.putExtra(URI_DATA,ItemsContract.Items.
                                                        buildItemUri(getItemId(vh.getAdapterPosition())).toString());
                    String transName = getString(R.string.trans_image_show);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        startActivityForResult(startAct,0,
                                            ActivityOptions.makeSceneTransitionAnimation(ArticleListActivity.this, vh
                                                    .thumbnailView, transName).toBundle());
                        setCallBack(vh.getAdapterPosition());
                    } else{
                        startActivity(intent);
                    }
                }
            });
            return vh;
        }

        private Date parsePublishedDate() {
            try {
                String date = mCursor.getString(ArticleLoader.Query.PUBLISHED_DATE);
                return dateFormat.parse(date);
            } catch (ParseException ex) {
                Log.e(TAG, ex.getMessage());
                Log.i(TAG, "passing today's date");
                return new Date();
            }
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            runEnterAnimation(holder.itemView, position);
            mCursor.moveToPosition(position);
            holder.titleView.setText(mCursor.getString(ArticleLoader.Query.TITLE));
            holder.setTagForView(position);
            Date publishedDate = parsePublishedDate();
            if (!publishedDate.before(START_OF_EPOCH.getTime())) {

                holder.subtitleView.setText(Html.fromHtml(
                        DateUtils.getRelativeTimeSpanString(
                                publishedDate.getTime(),
                                System.currentTimeMillis(), DateUtils.HOUR_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_ALL).toString()
                                + "<br/>" + " by "
                                + mCursor.getString(ArticleLoader.Query.AUTHOR)));
            } else {
                holder.subtitleView.setText(Html.fromHtml(
                        outputFormat.format(publishedDate)
                        + "<br/>" + " by "
                        + mCursor.getString(ArticleLoader.Query.AUTHOR)));
            }
            holder.thumbnailView.setImageUrl(
                    mCursor.getString(ArticleLoader.Query.THUMB_URL),
                    ImageLoaderHelper.getInstance(ArticleListActivity.this).getImageLoader());
            holder.thumbnailView.setAspectRatio(mCursor.getFloat(ArticleLoader.Query.ASPECT_RATIO));
        }

        @Override
        public int getItemCount() {
            return mCursor.getCount();
        }
    }





    public static class ViewHolder extends RecyclerView.ViewHolder {
        public DynamicHeightNetworkImageView thumbnailView;
        public TextView titleView;
        public TextView subtitleView;

        public ViewHolder(View view) {
            super(view);
            thumbnailView = (DynamicHeightNetworkImageView) view.findViewById(R.id.thumbnail);
            titleView = (TextView) view.findViewById(R.id.article_title);
            subtitleView = (TextView) view.findViewById(R.id.article_subtitle);


        }

        // quick hack for finding the correct view to animate
        public void setTagForView(int position){
            thumbnailView.setTag(position+"");
        }

    }
}
