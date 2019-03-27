/*
 * Copyright 2014 Magnus Woxblom
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.woxthebox.draglistview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Scroller;
import java.util.ArrayList;

import static android.support.v7.widget.RecyclerView.NO_POSITION;

public class BoardViewVertical extends ScrollView implements AutoScroller.AutoScrollListener {

    public interface BoardCallback {
        boolean canDragItemAtPosition(int column, int row);

        boolean canDropItemAtPosition(int oldColumn, int oldRow, int newColumn, int newRow);
    }

    public interface BoardListener {
        void onItemDragStarted(int column, int row);

        void onItemDragEnded(int fromColumn, int fromRow, int toColumn, int toRow);

        void onItemChangedPosition(int oldColumn, int oldRow, int newColumn, int newRow);

        void onItemChangedColumn(int oldColumn, int newColumn);

        void onFocusedColumnChanged(int oldColumn, int newColumn);

        void onColumnDragStarted(int position);

        void onColumnDragChangedPosition(int oldPosition, int newPosition);

        void onColumnDragEnded(int position);
    }

    public static abstract class BoardListenerAdapter implements BoardListener {
        @Override
        public void onItemDragStarted(int column, int row) {
        }

        @Override
        public void onItemDragEnded(int fromColumn, int fromRow, int toColumn, int toRow) {
        }

        @Override
        public void onItemChangedPosition(int oldColumn, int oldRow, int newColumn, int newRow) {
        }

        @Override
        public void onItemChangedColumn(int oldColumn, int newColumn) {
        }

        @Override
        public void onFocusedColumnChanged(int oldColumn, int newColumn) {
        }

        @Override
        public void onColumnDragStarted(int position) {
        }

        @Override
        public void onColumnDragChangedPosition(int oldPosition, int newPosition) {
        }

        @Override
        public void onColumnDragEnded(int position) {
        }
    }

    public enum ColumnSnapPosition {
        UP, CENTER, DOWN
    }

    private static final int SCROLL_ANIMATION_DURATION = 325;
    private Scroller mScroller;
    private AutoScroller mAutoScroller;
    private GestureDetector mGestureDetector;
    private FrameLayout mRootLayout;
    private LinearLayout mColumnLayout;
    private ArrayList<DragItemVerticalRecyclerView> mLists = new ArrayList<>();
    private ArrayList<View> mHeaders = new ArrayList<>();
    private DragItemVerticalRecyclerView mCurrentRecyclerView;
    private DragItemVertical mDragItem;
    private DragItemVertical mDragColumn;
    private BoardListener mBoardListener;
    private BoardCallback mBoardCallback;
    private boolean mSnapToColumnWhenScrolling = true;
    private boolean mSnapToColumnWhenDragging = true;
    private boolean mSnapToColumnInLandscape = false;
    private ColumnSnapPosition mSnapPosition = ColumnSnapPosition.CENTER;
    private int mCurrentColumn;
    private float mTouchX;
    private float mTouchY;
    private float mDragColumnStartScrollX;
    private float mDragColumnStartScrollY;
    private int mColumnWidth;
    private int mColumnHeight;
    private int mDragStartColumn;
    private int mDragStartRow;
    private boolean mHasLaidOut;
    private boolean mDragEnabled = true;
    private int mLastDragColumn = NO_POSITION;
    private int mLastDragRow = NO_POSITION;
    private SavedState mSavedState;
    private boolean mItemDraggingChanged;

    public BoardViewVertical(Context context) {
        super(context);
    }

    public BoardViewVertical(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BoardViewVertical(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        Resources res = getResources();
        boolean isPortrait =
                res.getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        if (isPortrait) {
            mColumnWidth = (int) (res.getDisplayMetrics().widthPixels * 0.87);
            mColumnHeight = (int) (res.getDisplayMetrics().heightPixels * 0.87);
        } else {
            mColumnWidth = (int) (res.getDisplayMetrics().density * 320);
            mColumnHeight = (int) (res.getDisplayMetrics().density * 320);
        }

        mGestureDetector = new GestureDetector(getContext(), new GestureListener());
        mScroller = new Scroller(getContext(), new DecelerateInterpolator(1.1f));
        mAutoScroller = new AutoScroller(getContext(), this);
        mAutoScroller.setAutoScrollMode(
                snapToColumnWhenDragging() ? AutoScroller.AutoScrollMode.COLUMN
                        : AutoScroller.AutoScrollMode.POSITION);
        mDragItem = new DragItemVertical(getContext());
        mDragColumn = new DragItemVertical(getContext());
        mDragColumn.setSnapToTouch(false);

        mRootLayout = new FrameLayout(getContext());
        mRootLayout.setLayoutParams(
                new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));

        mColumnLayout = new LinearLayout(getContext());
        mColumnLayout.setOrientation(LinearLayout.VERTICAL);
        mColumnLayout.setLayoutParams(
                new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        mColumnLayout.setMotionEventSplittingEnabled(false);

        mRootLayout.addView(mColumnLayout);
        mRootLayout.addView(mDragItem.getDragItemView());
        addView(mRootLayout);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        // Snap to closes column after first layout.
        // This is needed so correct column is scrolled to after a rotation.
        if (!mHasLaidOut && mSavedState != null) {
            mCurrentColumn = mSavedState.currentColumn;
            mSavedState = null;
            post(new Runnable() {
                @Override
                public void run() {
                    scrollToColumn(mCurrentColumn, false);
                }
            });
        }
        mHasLaidOut = true;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        mSavedState = ss;
        requestLayout();
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        return new SavedState(superState,
                snapToColumnWhenScrolling() ? mCurrentColumn : getClosestSnapColumn());
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        boolean retValue = handleTouchEvent(event);
        return retValue || super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean retValue = handleTouchEvent(event);
        return retValue || super.onTouchEvent(event);
    }

    private boolean handleTouchEvent(MotionEvent event) {
        if (mLists.size() == 0) {
            return false;
        }

        mTouchX = event.getX();
        mTouchY = event.getY();
        if (isDragging()) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_MOVE:
                    if (!mAutoScroller.isAutoScrolling()) {
                        updateScrollPosition();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mAutoScroller.stopAutoScroll();
                    if (isDraggingColumn()) {
                        endDragColumn();
                    } else {
                        mCurrentRecyclerView.onDragEnded();
                    }
                    if (snapToColumnWhenScrolling()) {
                        scrollToColumn(getColumnOfList(mCurrentRecyclerView), true);
                    }
                    invalidate();
                    break;
            }
            return true;
        } else {
            if (snapToColumnWhenScrolling() && mGestureDetector.onTouchEvent(event)) {
                // A page fling occurred, consume event
                return true;
            }
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (!mScroller.isFinished()) {
                        // View was grabbed during animation
                        mScroller.forceFinished(true);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (snapToColumnWhenScrolling()) {
                        scrollToColumn(getClosestSnapColumn(), true);
                    }
                    break;
            }
            return false;
        }
    }

    @Override
    public void computeScroll() {
        if (!mScroller.isFinished() && mScroller.computeScrollOffset()) {
            int x = mScroller.getCurrX();
            int y = mScroller.getCurrY();
            if (getScrollX() != x || getScrollY() != y) {
                scrollTo(x, y);
            }

            // If auto scrolling at the same time as the scroller is running,
            // then update the drag item position to prevent stuttering item
            if (mAutoScroller.isAutoScrolling() && isDragging()) {
                if (isDraggingColumn()) {
                    mDragColumn.setPosition(mTouchY + getScrollY() - mDragColumnStartScrollY,
                            mTouchY);
                } else {
                    mDragItem.setPosition(
                            getRelativeViewTouchX((View) mCurrentRecyclerView.getParent()),
                            getRelativeViewTouchY(mCurrentRecyclerView));
                }
            }

            ViewCompat.postInvalidateOnAnimation(this);
        } else if (!snapToColumnWhenScrolling()) {
            super.computeScroll();
        }
    }

    @Override
    public void onAutoScrollPositionBy(int dx, int dy) {
        if (isDragging()) {
            scrollBy(dx, dy);
            updateScrollPosition();
        } else {
            mAutoScroller.stopAutoScroll();
        }
    }

    @Override
    public void onAutoScrollColumnBy(int columns) {
        if (isDragging()) {
            int newColumn = mCurrentColumn + columns;
            if (columns != 0 && newColumn >= 0 && newColumn < mLists.size()) {
                scrollToColumn(newColumn, true);
            }
            updateScrollPosition();
        } else {
            mAutoScroller.stopAutoScroll();
        }
    }

    private void updateScrollPosition() {
        DragItemVerticalRecyclerView currentList = getCurrentRecyclerView(mTouchY + getScrollY());
        if (isDraggingColumn()) {
            if (mCurrentRecyclerView != currentList) {
                moveColumn(getColumnOfList(mCurrentRecyclerView), getColumnOfList(currentList));
            }
            // Need to subtract with scrollX at the beginning of the column drag because of how
            // drag item position is calculated
            mDragColumn.setPosition(mTouchX + getScrollX() - mDragColumnStartScrollX, mTouchY);
        } else {
            // Updated event to scrollview coordinates
            if (mCurrentRecyclerView != currentList) {
                int oldColumn = getColumnOfList(mCurrentRecyclerView);
                int newColumn = getColumnOfList(currentList);
                long itemId = mCurrentRecyclerView.getDragItemId();

                // Check if it is ok to drop the item in the new column first
                int newPosition =
                        currentList.getDragPositionForY(getRelativeViewTouchY(currentList));
                if (mBoardCallback == null || mBoardCallback.canDropItemAtPosition(mDragStartColumn,
                        mDragStartRow, newColumn, newPosition)) {
                    Object item = mCurrentRecyclerView.removeDragItemAndEnd();
                    if (item != null) {
                        mCurrentRecyclerView = currentList;
                        mCurrentRecyclerView.addDragItemAndStart(
                                getRelativeViewTouchY(mCurrentRecyclerView), item, itemId,
                                mItemDraggingChanged);
                        mDragItem.setOffset(((View) mCurrentRecyclerView.getParent()).getLeft(),
                                mCurrentRecyclerView.getTop());

                        if (mBoardListener != null) {
                            mBoardListener.onItemChangedColumn(oldColumn, newColumn);
                        }
                    }
                }
            }

            // Updated event to list coordinates
            mCurrentRecyclerView.onDragging(
                    getRelativeViewTouchX((View) mCurrentRecyclerView.getParent()),
                    getRelativeViewTouchY(mCurrentRecyclerView));
        }

        boolean isPortrait =
                getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        float scrollEdge =
                getResources().getDisplayMetrics().heightPixels * (isPortrait ? 0.18f : 0.14f);
        if (mTouchY > getHeight() - scrollEdge && getScrollY() < mColumnLayout.getHeight()) {
            mAutoScroller.startAutoScroll(AutoScroller.ScrollDirection.UP);
        } else if (mTouchY < scrollEdge && getScrollY() > 0) {
            mAutoScroller.startAutoScroll(AutoScroller.ScrollDirection.DOWN);
        } else {
            mAutoScroller.stopAutoScroll();
        }
        invalidate();
    }

    private float getRelativeViewTouchX(View view) {
        return mTouchX + getScrollX() - view.getLeft();
    }

    private float getRelativeViewTouchY(View view) {
        return mTouchY + getScrollY() - view.getTop();
    }

    private DragItemVerticalRecyclerView getCurrentRecyclerView(float y) {
        for (DragItemVerticalRecyclerView list : mLists) {
            View parent = (View) list.getParent();
            if (parent.getTop() <= y && parent.getBottom() >= y) {
                return list;
            }
        }
        return mCurrentRecyclerView;
    }

    private int getColumnOfList(DragItemVerticalRecyclerView list) {
        int column = 0;
        for (int i = 0; i < mLists.size(); i++) {
            RecyclerView tmpList = mLists.get(i);
            if (tmpList == list) {
                column = i;
            }
        }
        return column;
    }

    private int getCurrentColumn(float posX) {
        for (int i = 0; i < mLists.size(); i++) {
            RecyclerView list = mLists.get(i);
            View parent = (View) list.getParent();
            if (parent.getLeft() <= posX && parent.getRight() > posX) {
                return i;
            }
        }
        return 0;
    }

    private int getClosestSnapColumn() {
        int column = 0;
        int minDiffY = Integer.MAX_VALUE;
        for (int i = 0; i < mLists.size(); i++) {
            View listParent = (View) mLists.get(i).getParent();

            int diffY = 0;
            switch (mSnapPosition) {
                case UP:
                    int topPosY = getScrollY();
                    diffY = Math.abs(listParent.getTop() + topPosY);
                    break;
                case CENTER:
                    int middlePosY = getMeasuredHeight() - getScrollY() / 2;
                    diffY = Math.abs(listParent.getTop() + mColumnHeight / 2 - middlePosY);
                    break;
                case DOWN:
                    int bottomPosY = getMeasuredHeight() - getScrollY();
                    diffY = Math.abs(listParent.getBottom() - bottomPosY);
                    break;
            }

            if (diffY < minDiffY) {
                minDiffY = diffY;
                column = i;
            }
        }
        return column;
    }

    private boolean snapToColumnWhenScrolling() {
        boolean isPortrait =
                getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        return mSnapToColumnWhenScrolling && (isPortrait || mSnapToColumnInLandscape);
    }

    private boolean snapToColumnWhenDragging() {
        boolean isPortrait =
                getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        return mSnapToColumnWhenDragging && (isPortrait || mSnapToColumnInLandscape);
    }

    private boolean isDraggingColumn() {
        return mCurrentRecyclerView != null && mDragColumn.isDragging();
    }

    private boolean isDragging() {
        return mCurrentRecyclerView != null && (mCurrentRecyclerView.isDragging()
                || isDraggingColumn());
    }

    public RecyclerView getRecyclerView(int column) {
        if (column >= 0 && column < mLists.size()) {
            return mLists.get(column);
        }
        return null;
    }

    public DragItemAdapter getAdapter(int column) {
        if (column >= 0 && column < mLists.size()) {
            return (DragItemAdapter) mLists.get(column).getAdapter();
        }
        return null;
    }

    public int getItemCount() {
        int count = 0;
        for (DragItemVerticalRecyclerView list : mLists) {
            count += list.getAdapter().getItemCount();
        }
        return count;
    }

    public int getItemCount(int column) {
        if (mLists.size() > column) {
            return mLists.get(column).getAdapter().getItemCount();
        }
        return 0;
    }

    public int getColumnCount() {
        return mLists.size();
    }

    public View getHeaderView(int column) {
        return mHeaders.get(column);
    }

    /**
     * @return The index of the column with a specific header. If the header can't be found -1 is
     * returned.
     */
    public int getColumnOfHeader(View header) {
        for (int i = 0; i < mHeaders.size(); i++) {
            if (mHeaders.get(i) == header) {
                return i;
            }
        }
        return -1;
    }

    public void removeItem(int column, int row) {
        DragItemAdapter adapter = (DragItemAdapter) mLists.get(column).getAdapter();
        adapter.removeItem(row);
    }

    public void addItem(int column, int row, Object item, boolean scrollToItem) {
        DragItemAdapter adapter = (DragItemAdapter) mLists.get(column).getAdapter();
        adapter.addItem(row, item);
        if (scrollToItem) {
            scrollToItem(column, row, false);
        }
    }

    public void moveItem(int fromColumn, int fromRow, int toColumn, int toRow,
            boolean scrollToItem) {
        if (!isDragging()
                && mLists.size() > fromColumn
                && mLists.get(fromColumn).getAdapter().getItemCount() > fromRow
                && mLists.size() > toColumn
                && mLists.get(toColumn).getAdapter().getItemCount() >= toRow) {
            DragItemAdapter adapter = (DragItemAdapter) mLists.get(fromColumn).getAdapter();
            Object item = adapter.removeItem(fromRow);
            adapter = (DragItemAdapter) mLists.get(toColumn).getAdapter();
            adapter.addItem(toRow, item);
            if (scrollToItem) {
                scrollToItem(toColumn, toRow, false);
            }
        }
    }

    public void moveItem(long itemId, int toColumn, int toRow, boolean scrollToItem) {
        for (int i = 0; i < mLists.size(); i++) {
            RecyclerView.Adapter adapter = mLists.get(i).getAdapter();
            final int count = adapter.getItemCount();
            for (int j = 0; j < count; j++) {
                long id = adapter.getItemId(j);
                if (id == itemId) {
                    moveItem(i, j, toColumn, toRow, scrollToItem);
                    return;
                }
            }
        }
    }

    public void replaceItem(int column, int row, Object item, boolean scrollToItem) {
        if (!isDragging()
                && mLists.size() > column
                && mLists.get(column).getAdapter().getItemCount() > row) {
            DragItemAdapter adapter = (DragItemAdapter) mLists.get(column).getAdapter();
            adapter.removeItem(row);
            adapter.addItem(row, item);
            if (scrollToItem) {
                scrollToItem(column, row, false);
            }
        }
    }

    public void scrollToItem(int column, int row, boolean animate) {
        if (mLists.size() > column && mLists.get(column).getAdapter().getItemCount() > row) {
            mScroller.forceFinished(true);
            scrollToColumn(column, animate);
            if (animate) {
                mLists.get(column).smoothScrollToPosition(row);
            } else {
                mLists.get(column).scrollToPosition(row);
            }
        }
    }

    public void scrollToColumn(int column, boolean animate) {
        if (mLists.size() <= column) {
            return;
        }

        View parent = (View) mLists.get(column).getParent();
        int newY = 0;
        switch (mSnapPosition) {
            case UP:
                newY = parent.getTop();
                break;
            case CENTER:
                newY = parent.getTop() - (getMeasuredHeight() - parent.getMeasuredHeight()) / 2;
                break;
            case DOWN:
                newY = parent.getBottom() - getMeasuredHeight();
                break;
        }

        int maxScroll = mRootLayout.getMeasuredHeight() - getMeasuredHeight();
        newY = newY < 0 ? 0 : newY;
        newY = newY > maxScroll ? maxScroll : newY;
        if (getScrollY() != newY) {
            mScroller.forceFinished(true);
            if (animate) {
                mScroller.startScroll(getScrollX(), getScrollY(), 0, newY + getScrollY(),
                        SCROLL_ANIMATION_DURATION);
                ViewCompat.postInvalidateOnAnimation(this);
            } else {
                scrollTo(getScrollX(), newY);
            }
        }

        int oldColumn = mCurrentColumn;
        mCurrentColumn = column;
        if (mBoardListener != null && oldColumn != mCurrentColumn) {
            mBoardListener.onFocusedColumnChanged(oldColumn, mCurrentColumn);
        }
    }

    public void clearBoard() {
        int count = mLists.size();
        for (int i = count - 1; i >= 0; i--) {
            mColumnLayout.removeViewAt(i);
            mHeaders.remove(i);
            mLists.remove(i);
        }
    }

    public void removeColumn(int column) {
        if (column >= 0 && mLists.size() > column) {
            mColumnLayout.removeViewAt(column);
            mHeaders.remove(column);
            mLists.remove(column);
        }
    }

    public void itemDraggingChanged() {
        mItemDraggingChanged = true;
        mCurrentRecyclerView.itemDraggingChanged();
    }

    public boolean isDragEnabled() {
        return mDragEnabled;
    }

    public void setDragEnabled(boolean enabled) {
        mDragEnabled = enabled;
        if (mLists.size() > 0) {
            for (DragItemVerticalRecyclerView list : mLists) {
                list.setDragEnabled(mDragEnabled);
            }
        }
    }

    /**
     * @return The index of the currently focused column. If column snapping is not enabled this
     * will always return 0.
     */
    public int getFocusedColumn() {
        if (!snapToColumnWhenScrolling()) {
            return 0;
        }
        return mCurrentColumn;
    }

    /**
     * @param width the width of columns in both portrait and landscape. This must be called
     * before {@link #addColumn} is
     * called for the width to take effect.
     */
    public void setColumnWidth(int width) {
        mColumnWidth = width;
    }

    /**
     * @param snapToColumn true if scrolling should snap to columns. Only applies to portrait mode.
     */
    public void setSnapToColumnsWhenScrolling(boolean snapToColumn) {
        mSnapToColumnWhenScrolling = snapToColumn;
    }

    /**
     * @param snapToColumn true if dragging should snap to columns when dragging towards the edge
     * . Only applies to portrait mode.
     */
    public void setSnapToColumnWhenDragging(boolean snapToColumn) {
        mSnapToColumnWhenDragging = snapToColumn;
        mAutoScroller.setAutoScrollMode(
                snapToColumnWhenDragging() ? AutoScroller.AutoScrollMode.COLUMN
                        : AutoScroller.AutoScrollMode.POSITION);
    }

    /**
     * @param snapToColumnInLandscape true if dragging should snap to columns when dragging
     * towards the edge also in landscape mode.
     */
    public void setSnapToColumnInLandscape(boolean snapToColumnInLandscape) {
        mSnapToColumnInLandscape = snapToColumnInLandscape;
        mAutoScroller.setAutoScrollMode(
                snapToColumnWhenDragging() ? AutoScroller.AutoScrollMode.COLUMN
                        : AutoScroller.AutoScrollMode.POSITION);
    }

    /**
     * @param snapPosition determines what position a column will snap to. LEFT, CENTER or RIGHT.
     */
    public void setColumnSnapPosition(ColumnSnapPosition snapPosition) {
        mSnapPosition = snapPosition;
    }

    /**
     * @param snapToTouch true if the drag item should snap to touch position when a drag is
     * started.
     */
    public void setSnapDragItemToTouch(boolean snapToTouch) {
        mDragItem.setSnapToTouch(snapToTouch);
    }

    public void setBoardListener(BoardListener listener) {
        mBoardListener = listener;
    }

    public void setBoardCallback(BoardCallback callback) {
        mBoardCallback = callback;
    }

    /**
     * Set a custom drag item to control the visuals and animations when dragging a list item.
     */
    public void setCustomDragItem(DragItemVertical dragItem) {
        DragItemVertical newDragItem =
                dragItem != null ? dragItem : new DragItemVertical(getContext());
        newDragItem.setSnapToTouch(mDragItem.isSnapToTouch());
        mDragItem = newDragItem;
        mRootLayout.removeViewAt(1);
        mRootLayout.addView(mDragItem.getDragItemView());
    }

    /**
     * Set a custom drag item to control the visuals and animations when dragging a column.
     */
    public void setCustomColumnDragItem(DragItemVertical dragItem) {
        mDragColumn = dragItem != null ? dragItem : new DragItemVertical(getContext());
    }

    private void startDragColumn(DragItemVerticalRecyclerView recyclerView, float posX,
            float posY) {
        mDragColumnStartScrollX = getScrollX();
        mDragColumnStartScrollY = getScrollY();
        mCurrentRecyclerView = recyclerView;

        View columnView = mColumnLayout.getChildAt(getColumnOfList(recyclerView));
        mDragColumn.startDrag(columnView, posX, posY);
        mRootLayout.addView(mDragColumn.getDragItemView());
        columnView.setAlpha(0);

        if (mBoardListener != null) {
            mBoardListener.onColumnDragStarted(getColumnOfList(mCurrentRecyclerView));
        }
    }

    private void endDragColumn() {
        mDragColumn.endDrag(mDragColumn.getRealDragView(), new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mDragColumn.getRealDragView().setAlpha(1);
                mDragColumn.hide();
                mRootLayout.removeView(mDragColumn.getDragItemView());

                if (mBoardListener != null) {
                    mBoardListener.onColumnDragEnded(getColumnOfList(mCurrentRecyclerView));
                }
            }
        });
    }

    private void moveColumn(final int fromIndex, final int toIndex) {
        DragItemVerticalRecyclerView list = mLists.remove(fromIndex);
        mLists.add(toIndex, list);

        View header = mHeaders.remove(fromIndex);
        mHeaders.add(toIndex, header);

        final View column1 = mColumnLayout.getChildAt(fromIndex);
        final View column2 = mColumnLayout.getChildAt(toIndex);
        mColumnLayout.removeViewAt(fromIndex);
        mColumnLayout.addView(column1, toIndex);

        mColumnLayout.addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                mColumnLayout.removeOnLayoutChangeListener(this);
                if (fromIndex > toIndex) {
                    column2.setTranslationY(
                            column2.getTranslationY() + column1.getTop() - column1.getBottom());
                } else {
                    column2.setTranslationY(
                            column2.getTranslationY() + column1.getTop() - column2.getTop());
                }
                column2.animate().translationY(0).setDuration(350).start();
            }
        });

        if (mBoardListener != null) {
            mBoardListener.onColumnDragChangedPosition(fromIndex, toIndex);
        }
    }

    /**
     * Inserts a column to the board at a specific index.
     *
     * @param adapter Adapter with the items for the column.
     * @param index Index where on the board to add the column.
     * @param header Header view that will be positioned above the column. Can be null.
     * @param columnDragView View that will act as handle to drag and drop columns. Can be null.
     * @param hasFixedItemSize If the items will have a fixed or dynamic size.
     * @return The created DragItemRecyclerView.
     */
    public DragItemVerticalRecyclerView insertColumn(final DragItemAdapter adapter, int index,
            final @Nullable View header, @Nullable View columnDragView, boolean hasFixedItemSize) {
        final DragItemVerticalRecyclerView recyclerView =
                insertColumn(adapter, index, header, hasFixedItemSize);
        setupColumnDragListener(columnDragView, recyclerView);
        return recyclerView;
    }

    /**
     * Adds a column at the last index of the board.
     *
     * @param adapter Adapter with the items for the column.
     * @param header Header view that will be positioned above the column. Can be null.
     * @param columnDragView View that will act as handle to drag and drop columns. Can be null.
     * @param hasFixedItemSize If the items will have a fixed or dynamic size.
     * @return The created DragItemRecyclerView.
     */
    public DragItemVerticalRecyclerView addColumn(final DragItemAdapter adapter,
            final @Nullable View header, @Nullable View columnDragView, boolean hasFixedItemSize) {
        final DragItemVerticalRecyclerView recyclerView =
                insertColumn(adapter, getColumnCount(), header, hasFixedItemSize);
        setupColumnDragListener(columnDragView, recyclerView);
        return recyclerView;
    }

    private void setupColumnDragListener(View columnDragView,
            final DragItemVerticalRecyclerView recyclerView) {
        if (columnDragView != null) {
            columnDragView.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    startDragColumn(recyclerView, mTouchX, mTouchY);
                    return true;
                }
            });
        }
    }

    private DragItemVerticalRecyclerView insertColumn(final DragItemAdapter adapter, int index,
            final @Nullable View header, boolean hasFixedItemSize) {
        if (index > getColumnCount()) {
            throw new IllegalArgumentException("Index is out of bounds");
        }

        final DragItemVerticalRecyclerView recyclerView =
                (DragItemVerticalRecyclerView) LayoutInflater.from(getContext())
                        .inflate(R.layout.drag_item_vertical_recycler_view, this, false);
        recyclerView.setId(getColumnCount());
        recyclerView.setHorizontalScrollBarEnabled(false);
        recyclerView.setVerticalScrollBarEnabled(false);
        recyclerView.setMotionEventSplittingEnabled(false);
        recyclerView.setDragItem(mDragItem);
        recyclerView.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setHasFixedSize(hasFixedItemSize);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.setDragItemListener(new DragItemVerticalRecyclerView.DragItemListener() {
            @Override
            public void onDragStarted(int itemPosition, float x, float y) {
                mDragStartColumn = getColumnOfList(recyclerView);
                mDragStartRow = itemPosition;
                mCurrentRecyclerView = recyclerView;
                //Set position Y dragItem draw
                mDragItem.setOffset(((View) mCurrentRecyclerView.getParent()).getX(),
                        mCurrentRecyclerView.getY());
                if (mBoardListener != null) {
                    mBoardListener.onItemDragStarted(mDragStartColumn, mDragStartRow);
                }
                invalidate();
            }

            @Override
            public void onDragging(int itemPosition, float x, float y) {
                int column = getColumnOfList(recyclerView);
                boolean positionChanged = column != mLastDragColumn || itemPosition != mLastDragRow;
                if (mBoardListener != null && positionChanged) {
                    mLastDragColumn = column;
                    mLastDragRow = itemPosition;
                    mBoardListener.onItemChangedPosition(mDragStartColumn, mDragStartRow, column,
                            itemPosition);
                }
            }

            @Override
            public void onDragEnded(int newItemPosition) {
                mLastDragColumn = NO_POSITION;
                mLastDragRow = NO_POSITION;
                mItemDraggingChanged = false;
                if (mBoardListener != null) {
                    mBoardListener.onItemDragEnded(mDragStartColumn, mDragStartRow,
                            getColumnOfList(recyclerView), newItemPosition);
                }
            }
        });
        recyclerView.setDragItemCallback(new DragItemVerticalRecyclerView.DragItemCallback() {
            @Override
            public boolean canDragItemAtPosition(int dragPosition) {
                int column = getColumnOfList(recyclerView);
                return mBoardCallback == null || mBoardCallback.canDragItemAtPosition(column,
                        dragPosition);
            }

            @Override
            public boolean canDropItemAtPosition(int dropPosition) {
                int column = getColumnOfList(recyclerView);
                return mBoardCallback == null || mBoardCallback.canDropItemAtPosition(
                        mDragStartColumn, mDragStartRow, column, dropPosition);
            }
        });

        recyclerView.setAdapter(adapter);
        recyclerView.setDragEnabled(mDragEnabled);
        adapter.setDragStartedListener(new DragItemAdapter.DragStartCallback() {
            @Override
            public boolean startDrag(View itemView, long itemId) {
                return recyclerView.startDrag(itemView, itemId,
                        getRelativeViewTouchX((View) recyclerView.getParent()),
                        getRelativeViewTouchY(recyclerView));
            }

            @Override
            public boolean isDragging() {
                return recyclerView.isDragging();
            }
        });

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new LayoutParams(mColumnWidth, LayoutParams.MATCH_PARENT));
        View columnHeader = header;
        if (header == null) {
            columnHeader = new View(getContext());
            columnHeader.setVisibility(View.GONE);
        }
        layout.addView(columnHeader);
        mHeaders.add(columnHeader);

        layout.addView(recyclerView);

        mLists.add(index, recyclerView);
        mColumnLayout.addView(layout, index);
        return recyclerView;
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        private float mStartScrollY;
        private int mStartColumn;

        @Override
        public boolean onDown(MotionEvent e) {
            mStartScrollY = getScrollY();
            mStartColumn = mCurrentColumn;
            return super.onDown(e);
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            // Calc new column to scroll to
            int closestColumn = getClosestSnapColumn();
            int newColumn = closestColumn;

            // This can happen if you start to drag in one direction and then fling in the other
            // direction.
            // We should then switch column in the fling direction.
            boolean wrongSnapDirection = newColumn > mStartColumn && velocityY > 0
                    || newColumn < mStartColumn && velocityY < 0;

            if (mStartScrollY == getScrollY()) {
                newColumn = mStartColumn;
            } else if (mStartColumn == closestColumn || wrongSnapDirection) {
                if (velocityY < 0) {
                    newColumn = closestColumn + 1;
                } else {
                    newColumn = closestColumn - 1;
                }
            }

            if (newColumn < 0 || newColumn > mLists.size() - 1) {
                newColumn = newColumn < 0 ? 0 : mLists.size() - 1;
            }

            // Calc new scrollY position
            scrollToColumn(newColumn, true);
            return true;
        }
    }

    @SuppressWarnings("WeakerAccess")
    static class SavedState extends BaseSavedState {
        public int currentColumn;

        private SavedState(Parcelable superState, int currentColumn) {
            super(superState);
            this.currentColumn = currentColumn;
        }

        public SavedState(Parcel source) {
            super(source);
            currentColumn = source.readInt();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(currentColumn);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }
}
