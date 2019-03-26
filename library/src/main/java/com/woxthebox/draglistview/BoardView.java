//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.woxthebox.draglistview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.Resources;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.Adapter;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.View.BaseSavedState;
import android.view.View.OnLayoutChangeListener;
import android.view.View.OnLongClickListener;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.Scroller;
import android.widget.FrameLayout.LayoutParams;
import com.woxthebox.draglistview.AutoScroller.AutoScrollListener;
import com.woxthebox.draglistview.AutoScroller.AutoScrollMode;
import com.woxthebox.draglistview.AutoScroller.ScrollDirection;
import com.woxthebox.draglistview.DragItemAdapter.DragStartCallback;
import com.woxthebox.draglistview.DragItemRecyclerView.DragItemCallback;
import com.woxthebox.draglistview.DragItemRecyclerView.DragItemListener;
import com.woxthebox.draglistview.R.layout;
import java.util.ArrayList;
import java.util.Iterator;

public class BoardView extends HorizontalScrollView implements AutoScrollListener {
    private static final int SCROLL_ANIMATION_DURATION = 325;
    private Scroller mScroller;
    private AutoScroller mAutoScroller;
    private GestureDetector mGestureDetector;
    private FrameLayout mRootLayout;
    private LinearLayout mColumnLayout;
    private ArrayList<DragItemRecyclerView> mLists = new ArrayList();
    private ArrayList<View> mHeaders = new ArrayList();
    private DragItemRecyclerView mCurrentRecyclerView;
    private DragItem mDragItem;
    private DragItem mDragColumn;
    private BoardView.BoardListener mBoardListener;
    private BoardView.BoardCallback mBoardCallback;
    private boolean mSnapToColumnWhenScrolling = true;
    private boolean mSnapToColumnWhenDragging = true;
    private boolean mSnapToColumnInLandscape = false;
    private BoardView.ColumnSnapPosition mSnapPosition;
    private int mCurrentColumn;
    private float mTouchX;
    private float mTouchY;
    private float mDragColumnStartScrollX;
    private int mColumnWidth;
    private int mDragStartColumn;
    private int mDragStartRow;
    private boolean mHasLaidOut;
    private boolean mDragEnabled;
    private int mLastDragColumn;
    private int mLastDragRow;
    private BoardView.SavedState mSavedState;
    private boolean mItemDraggingChanged;

    public BoardView(Context context) {
        super(context);
        this.mSnapPosition = BoardView.ColumnSnapPosition.CENTER;
        this.mDragEnabled = true;
        this.mLastDragColumn = -1;
        this.mLastDragRow = -1;
    }

    public BoardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mSnapPosition = BoardView.ColumnSnapPosition.CENTER;
        this.mDragEnabled = true;
        this.mLastDragColumn = -1;
        this.mLastDragRow = -1;
    }

    public BoardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.mSnapPosition = BoardView.ColumnSnapPosition.CENTER;
        this.mDragEnabled = true;
        this.mLastDragColumn = -1;
        this.mLastDragRow = -1;
    }

    protected void onFinishInflate() {
        super.onFinishInflate();
        Resources res = this.getResources();
        boolean isPortrait = res.getConfiguration().orientation == 1;
        if (isPortrait) {
            this.mColumnWidth = (int)((double)res.getDisplayMetrics().widthPixels * 0.87D);
        } else {
            this.mColumnWidth = (int)(res.getDisplayMetrics().density * 320.0F);
        }

        this.mGestureDetector = new GestureDetector(this.getContext(), new BoardView.GestureListener(null));
        this.mScroller = new Scroller(this.getContext(), new DecelerateInterpolator(1.1F));
        this.mAutoScroller = new AutoScroller(this.getContext(), this);
        this.mAutoScroller.setAutoScrollMode(this.snapToColumnWhenDragging() ? AutoScrollMode.COLUMN : AutoScrollMode.POSITION);
        this.mDragItem = new DragItem(this.getContext());
        this.mDragColumn = new DragItem(this.getContext());
        this.mDragColumn.setSnapToTouch(false);
        this.mRootLayout = new FrameLayout(this.getContext());
        this.mRootLayout.setLayoutParams(new LayoutParams(-2, -1));
        this.mColumnLayout = new LinearLayout(this.getContext());
        this.mColumnLayout.setOrientation(0);
        this.mColumnLayout.setLayoutParams(new LayoutParams(-2, -1));
        this.mColumnLayout.setMotionEventSplittingEnabled(false);
        this.mRootLayout.addView(this.mColumnLayout);
        this.mRootLayout.addView(this.mDragItem.getDragItemView());
        this.addView(this.mRootLayout);
    }

    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (!this.mHasLaidOut && this.mSavedState != null) {
            this.mCurrentColumn = this.mSavedState.currentColumn;
            this.mSavedState = null;
            this.post(new Runnable() {
                public void run() {
                    BoardView.this.scrollToColumn(BoardView.this.mCurrentColumn, false);
                }
            });
        }

        this.mHasLaidOut = true;
    }

    protected void onRestoreInstanceState(Parcelable state) {
        BoardView.SavedState ss = (BoardView.SavedState)state;
        super.onRestoreInstanceState(ss.getSuperState());
        this.mSavedState = ss;
        this.requestLayout();
    }

    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        return new BoardView.SavedState(superState, this.snapToColumnWhenScrolling() ? this.mCurrentColumn : this.getClosestSnapColumn(), null);
    }

    public boolean onInterceptTouchEvent(MotionEvent event) {
        boolean retValue = this.handleTouchEvent(event);
        return retValue || super.onInterceptTouchEvent(event);
    }

    public boolean onTouchEvent(MotionEvent event) {
        boolean retValue = this.handleTouchEvent(event);
        return retValue || super.onTouchEvent(event);
    }

    private boolean handleTouchEvent(MotionEvent event) {
        if (this.mLists.size() == 0) {
            return false;
        } else {
            this.mTouchX = event.getX();
            this.mTouchY = event.getY();
            if (this.isDragging()) {
                switch(event.getAction()) {
                    case 1:
                    case 3:
                        this.mAutoScroller.stopAutoScroll();
                        if (this.isDraggingColumn()) {
                            this.endDragColumn();
                        } else {
                            this.mCurrentRecyclerView.onDragEnded();
                        }

                        if (this.snapToColumnWhenScrolling()) {
                            this.scrollToColumn(this.getColumnOfList(this.mCurrentRecyclerView), true);
                        }

                        this.invalidate();
                        break;
                    case 2:
                        if (!this.mAutoScroller.isAutoScrolling()) {
                            this.updateScrollPosition();
                        }
                }

                return true;
            } else if (this.snapToColumnWhenScrolling() && this.mGestureDetector.onTouchEvent(event)) {
                return true;
            } else {
                switch(event.getAction()) {
                    case 0:
                        if (!this.mScroller.isFinished()) {
                            this.mScroller.forceFinished(true);
                        }
                        break;
                    case 1:
                    case 3:
                        if (this.snapToColumnWhenScrolling()) {
                            this.scrollToColumn(this.getClosestSnapColumn(), true);
                        }
                    case 2:
                }

                return false;
            }
        }
    }

    public void computeScroll() {
        if (!this.mScroller.isFinished() && this.mScroller.computeScrollOffset()) {
            int x = this.mScroller.getCurrX();
            int y = this.mScroller.getCurrY();
            if (this.getScrollX() != x || this.getScrollY() != y) {
                this.scrollTo(x, y);
            }

            if (this.mAutoScroller.isAutoScrolling() && this.isDragging()) {
                if (this.isDraggingColumn()) {
                    this.mDragColumn.setPosition(this.mTouchX + (float)this.getScrollX() - this.mDragColumnStartScrollX, this.mTouchY);
                } else {
                    this.mDragItem.setPosition(this.getRelativeViewTouchX((View)this.mCurrentRecyclerView.getParent()), this.getRelativeViewTouchY(this.mCurrentRecyclerView));
                }
            }

            ViewCompat.postInvalidateOnAnimation(this);
        } else if (!this.snapToColumnWhenScrolling()) {
            super.computeScroll();
        }

    }

    public void onAutoScrollPositionBy(int dx, int dy) {
        if (this.isDragging()) {
            this.scrollBy(dx, dy);
            this.updateScrollPosition();
        } else {
            this.mAutoScroller.stopAutoScroll();
        }

    }

    public void onAutoScrollColumnBy(int columns) {
        if (this.isDragging()) {
            int newColumn = this.mCurrentColumn + columns;
            if (columns != 0 && newColumn >= 0 && newColumn < this.mLists.size()) {
                this.scrollToColumn(newColumn, true);
            }

            this.updateScrollPosition();
        } else {
            this.mAutoScroller.stopAutoScroll();
        }

    }

    private void updateScrollPosition() {
        DragItemRecyclerView currentList;
        if (this.isDraggingColumn()) {
            currentList = this.getCurrentRecyclerView(this.mTouchX + (float)this.getScrollX());
            if (this.mCurrentRecyclerView != currentList) {
                this.moveColumn(this.getColumnOfList(this.mCurrentRecyclerView), this.getColumnOfList(currentList));
            }

            this.mDragColumn.setPosition(this.mTouchX + (float)this.getScrollX() - this.mDragColumnStartScrollX, this.mTouchY);
        } else {
            currentList = this.getCurrentRecyclerView(this.mTouchX + (float)this.getScrollX());
            if (this.mCurrentRecyclerView != currentList) {
                int oldColumn = this.getColumnOfList(this.mCurrentRecyclerView);
                int newColumn = this.getColumnOfList(currentList);
                long itemId = this.mCurrentRecyclerView.getDragItemId();
                int newPosition = currentList.getDragPositionForY(this.getRelativeViewTouchY(currentList));
                if (this.mBoardCallback == null || this.mBoardCallback.canDropItemAtPosition(this.mDragStartColumn, this.mDragStartRow, newColumn, newPosition)) {
                    Object item = this.mCurrentRecyclerView.removeDragItemAndEnd();
                    if (item != null) {
                        this.mCurrentRecyclerView = currentList;
                        this.mCurrentRecyclerView.addDragItemAndStart(this.getRelativeViewTouchY(this.mCurrentRecyclerView), item, itemId, this.mItemDraggingChanged);
                        this.mDragItem.setOffset((float)((View)this.mCurrentRecyclerView.getParent()).getLeft(), (float)this.mCurrentRecyclerView.getTop());
                        if (this.mBoardListener != null) {
                            this.mBoardListener.onItemChangedColumn(oldColumn, newColumn);
                        }
                    }
                }
            }

            this.mCurrentRecyclerView.onDragging(this.getRelativeViewTouchX((View)this.mCurrentRecyclerView.getParent()), this.getRelativeViewTouchY(this.mCurrentRecyclerView));
        }

        boolean isPortrait = this.getResources().getConfiguration().orientation == 1;
        float scrollEdge = (float)this.getResources().getDisplayMetrics().widthPixels * (isPortrait ? 0.18F : 0.14F);
        if (this.mTouchX > (float)this.getWidth() - scrollEdge && this.getScrollX() < this.mColumnLayout.getWidth()) {
            this.mAutoScroller.startAutoScroll(ScrollDirection.LEFT);
        } else if (this.mTouchX < scrollEdge && this.getScrollX() > 0) {
            this.mAutoScroller.startAutoScroll(ScrollDirection.RIGHT);
        } else {
            this.mAutoScroller.stopAutoScroll();
        }

        this.invalidate();
    }

    private float getRelativeViewTouchX(View view) {
        return this.mTouchX + (float)this.getScrollX() - (float)view.getLeft();
    }

    private float getRelativeViewTouchY(View view) {
        return this.mTouchY - (float)view.getTop();
    }

    private DragItemRecyclerView getCurrentRecyclerView(float x) {
        Iterator var2 = this.mLists.iterator();

        DragItemRecyclerView list;
        View parent;
        do {
            if (!var2.hasNext()) {
                return this.mCurrentRecyclerView;
            }

            list = (DragItemRecyclerView)var2.next();
            parent = (View)list.getParent();
        } while((float)parent.getLeft() > x || (float)parent.getRight() <= x);

        return list;
    }

    private int getColumnOfList(DragItemRecyclerView list) {
        int column = 0;

        for(int i = 0; i < this.mLists.size(); ++i) {
            RecyclerView tmpList = (RecyclerView)this.mLists.get(i);
            if (tmpList == list) {
                column = i;
            }
        }

        return column;
    }

    private int getCurrentColumn(float posX) {
        for(int i = 0; i < this.mLists.size(); ++i) {
            RecyclerView list = (RecyclerView)this.mLists.get(i);
            View parent = (View)list.getParent();
            if ((float)parent.getLeft() <= posX && (float)parent.getRight() > posX) {
                return i;
            }
        }

        return 0;
    }

    private int getClosestSnapColumn() {
        int column = 0;
        int minDiffX = 2147483647;

        for(int i = 0; i < this.mLists.size(); ++i) {
            View listParent = (View)((DragItemRecyclerView)this.mLists.get(i)).getParent();
            int diffX = 0;
            switch(this.mSnapPosition) {
                case LEFT:
                    int leftPosX = this.getScrollX();
                    diffX = Math.abs(listParent.getLeft() - leftPosX);
                    break;
                case CENTER:
                    int middlePosX = this.getScrollX() + this.getMeasuredWidth() / 2;
                    diffX = Math.abs(listParent.getLeft() + this.mColumnWidth / 2 - middlePosX);
                    break;
                case RIGHT:
                    int rightPosX = this.getScrollX() + this.getMeasuredWidth();
                    diffX = Math.abs(listParent.getRight() - rightPosX);
            }

            if (diffX < minDiffX) {
                minDiffX = diffX;
                column = i;
            }
        }

        return column;
    }

    private boolean snapToColumnWhenScrolling() {
        boolean isPortrait = this.getResources().getConfiguration().orientation == 1;
        return this.mSnapToColumnWhenScrolling && (isPortrait || this.mSnapToColumnInLandscape);
    }

    private boolean snapToColumnWhenDragging() {
        boolean isPortrait = this.getResources().getConfiguration().orientation == 1;
        return this.mSnapToColumnWhenDragging && (isPortrait || this.mSnapToColumnInLandscape);
    }

    private boolean isDraggingColumn() {
        return this.mCurrentRecyclerView != null && this.mDragColumn.isDragging();
    }

    private boolean isDragging() {
        return this.mCurrentRecyclerView != null && (this.mCurrentRecyclerView.isDragging() || this.isDraggingColumn());
    }

    public RecyclerView getRecyclerView(int column) {
        return column >= 0 && column < this.mLists.size() ? (RecyclerView)this.mLists.get(column) : null;
    }

    public DragItemAdapter getAdapter(int column) {
        return column >= 0 && column < this.mLists.size() ? (DragItemAdapter)((DragItemRecyclerView)this.mLists.get(column)).getAdapter() : null;
    }

    public int getItemCount() {
        int count = 0;

        DragItemRecyclerView list;
        for(Iterator var2 = this.mLists.iterator(); var2.hasNext(); count += list.getAdapter().getItemCount()) {
            list = (DragItemRecyclerView)var2.next();
        }

        return count;
    }

    public int getItemCount(int column) {
        return this.mLists.size() > column ? ((DragItemRecyclerView)this.mLists.get(column)).getAdapter().getItemCount() : 0;
    }

    public int getColumnCount() {
        return this.mLists.size();
    }

    public View getHeaderView(int column) {
        return (View)this.mHeaders.get(column);
    }

    public int getColumnOfHeader(View header) {
        for(int i = 0; i < this.mHeaders.size(); ++i) {
            if (this.mHeaders.get(i) == header) {
                return i;
            }
        }

        return -1;
    }

    public void removeItem(int column, int row) {
        DragItemAdapter adapter = (DragItemAdapter)((DragItemRecyclerView)this.mLists.get(column)).getAdapter();
        adapter.removeItem(row);
    }

    public void addItem(int column, int row, Object item, boolean scrollToItem) {
        DragItemAdapter adapter = (DragItemAdapter)((DragItemRecyclerView)this.mLists.get(column)).getAdapter();
        adapter.addItem(row, item);
        if (scrollToItem) {
            this.scrollToItem(column, row, false);
        }

    }

    public void moveItem(int fromColumn, int fromRow, int toColumn, int toRow, boolean scrollToItem) {
        if (!this.isDragging() && this.mLists.size() > fromColumn && ((DragItemRecyclerView)this.mLists.get(fromColumn)).getAdapter().getItemCount() > fromRow && this.mLists.size() > toColumn && ((DragItemRecyclerView)this.mLists.get(toColumn)).getAdapter().getItemCount() >= toRow) {
            DragItemAdapter adapter = (DragItemAdapter)((DragItemRecyclerView)this.mLists.get(fromColumn)).getAdapter();
            Object item = adapter.removeItem(fromRow);
            adapter = (DragItemAdapter)((DragItemRecyclerView)this.mLists.get(toColumn)).getAdapter();
            adapter.addItem(toRow, item);
            if (scrollToItem) {
                this.scrollToItem(toColumn, toRow, false);
            }
        }

    }

    public void moveItem(long itemId, int toColumn, int toRow, boolean scrollToItem) {
        for(int i = 0; i < this.mLists.size(); ++i) {
            Adapter adapter = ((DragItemRecyclerView)this.mLists.get(i)).getAdapter();
            int count = adapter.getItemCount();

            for(int j = 0; j < count; ++j) {
                long id = adapter.getItemId(j);
                if (id == itemId) {
                    this.moveItem(i, j, toColumn, toRow, scrollToItem);
                    return;
                }
            }
        }

    }

    public void replaceItem(int column, int row, Object item, boolean scrollToItem) {
        if (!this.isDragging() && this.mLists.size() > column && ((DragItemRecyclerView)this.mLists.get(column)).getAdapter().getItemCount() > row) {
            DragItemAdapter adapter = (DragItemAdapter)((DragItemRecyclerView)this.mLists.get(column)).getAdapter();
            adapter.removeItem(row);
            adapter.addItem(row, item);
            if (scrollToItem) {
                this.scrollToItem(column, row, false);
            }
        }

    }

    public void scrollToItem(int column, int row, boolean animate) {
        if (this.mLists.size() > column && ((DragItemRecyclerView)this.mLists.get(column)).getAdapter().getItemCount() > row) {
            this.mScroller.forceFinished(true);
            this.scrollToColumn(column, animate);
            if (animate) {
                ((DragItemRecyclerView)this.mLists.get(column)).smoothScrollToPosition(row);
            } else {
                ((DragItemRecyclerView)this.mLists.get(column)).scrollToPosition(row);
            }
        }

    }

    public void scrollToColumn(int column, boolean animate) {
        if (this.mLists.size() > column) {
            View parent = (View)((DragItemRecyclerView)this.mLists.get(column)).getParent();
            int newX = 0;
            switch(this.mSnapPosition) {
                case LEFT:
                    newX = parent.getLeft();
                    break;
                case CENTER:
                    newX = parent.getLeft() - (this.getMeasuredWidth() - parent.getMeasuredWidth()) / 2;
                    break;
                case RIGHT:
                    newX = parent.getRight() - this.getMeasuredWidth();
            }

            int maxScroll = this.mRootLayout.getMeasuredWidth() - this.getMeasuredWidth();
            newX = newX < 0 ? 0 : newX;
            newX = newX > maxScroll ? maxScroll : newX;
            if (this.getScrollX() != newX) {
                this.mScroller.forceFinished(true);
                if (animate) {
                    this.mScroller.startScroll(this.getScrollX(), this.getScrollY(), newX - this.getScrollX(), 0, 325);
                    ViewCompat.postInvalidateOnAnimation(this);
                } else {
                    this.scrollTo(newX, this.getScrollY());
                }
            }

            int oldColumn = this.mCurrentColumn;
            this.mCurrentColumn = column;
            if (this.mBoardListener != null && oldColumn != this.mCurrentColumn) {
                this.mBoardListener.onFocusedColumnChanged(oldColumn, this.mCurrentColumn);
            }

        }
    }

    public void clearBoard() {
        int count = this.mLists.size();

        for(int i = count - 1; i >= 0; --i) {
            this.mColumnLayout.removeViewAt(i);
            this.mHeaders.remove(i);
            this.mLists.remove(i);
        }

    }

    public void removeColumn(int column) {
        if (column >= 0 && this.mLists.size() > column) {
            this.mColumnLayout.removeViewAt(column);
            this.mHeaders.remove(column);
            this.mLists.remove(column);
        }

    }

    public void itemDraggingChanged() {
        this.mItemDraggingChanged = true;
        this.mCurrentRecyclerView.itemDraggingChanged();
    }

    public boolean isDragEnabled() {
        return this.mDragEnabled;
    }

    public void setDragEnabled(boolean enabled) {
        this.mDragEnabled = enabled;
        if (this.mLists.size() > 0) {
            Iterator var2 = this.mLists.iterator();

            while(var2.hasNext()) {
                DragItemRecyclerView list = (DragItemRecyclerView)var2.next();
                list.setDragEnabled(this.mDragEnabled);
            }
        }

    }

    public int getFocusedColumn() {
        return !this.snapToColumnWhenScrolling() ? 0 : this.mCurrentColumn;
    }

    public void setColumnWidth(int width) {
        this.mColumnWidth = width;
    }

    public void setSnapToColumnsWhenScrolling(boolean snapToColumn) {
        this.mSnapToColumnWhenScrolling = snapToColumn;
    }

    public void setSnapToColumnWhenDragging(boolean snapToColumn) {
        this.mSnapToColumnWhenDragging = snapToColumn;
        this.mAutoScroller.setAutoScrollMode(this.snapToColumnWhenDragging() ? AutoScrollMode.COLUMN : AutoScrollMode.POSITION);
    }

    public void setSnapToColumnInLandscape(boolean snapToColumnInLandscape) {
        this.mSnapToColumnInLandscape = snapToColumnInLandscape;
        this.mAutoScroller.setAutoScrollMode(this.snapToColumnWhenDragging() ? AutoScrollMode.COLUMN : AutoScrollMode.POSITION);
    }

    public void setColumnSnapPosition(BoardView.ColumnSnapPosition snapPosition) {
        this.mSnapPosition = snapPosition;
    }

    public void setSnapDragItemToTouch(boolean snapToTouch) {
        this.mDragItem.setSnapToTouch(snapToTouch);
    }

    public void setBoardListener(BoardView.BoardListener listener) {
        this.mBoardListener = listener;
    }

    public void setBoardCallback(BoardView.BoardCallback callback) {
        this.mBoardCallback = callback;
    }

    public void setCustomDragItem(DragItem dragItem) {
        DragItem newDragItem = dragItem != null ? dragItem : new DragItem(this.getContext());
        newDragItem.setSnapToTouch(this.mDragItem.isSnapToTouch());
        this.mDragItem = newDragItem;
        this.mRootLayout.removeViewAt(1);
        this.mRootLayout.addView(this.mDragItem.getDragItemView());
    }

    public void setCustomColumnDragItem(DragItem dragItem) {
        this.mDragColumn = dragItem != null ? dragItem : new DragItem(this.getContext());
    }

    private void startDragColumn(DragItemRecyclerView recyclerView, float posX, float posY) {
        this.mDragColumnStartScrollX = (float)this.getScrollX();
        this.mCurrentRecyclerView = recyclerView;
        View columnView = this.mColumnLayout.getChildAt(this.getColumnOfList(recyclerView));
        this.mDragColumn.startDrag(columnView, posX, posY);
        this.mRootLayout.addView(this.mDragColumn.getDragItemView());
        columnView.setAlpha(0.0F);
        if (this.mBoardListener != null) {
            this.mBoardListener.onColumnDragStarted(this.getColumnOfList(this.mCurrentRecyclerView));
        }

    }

    private void endDragColumn() {
        this.mDragColumn.endDrag(this.mDragColumn.getRealDragView(), new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator animation) {
                BoardView.this.mDragColumn.getRealDragView().setAlpha(1.0F);
                BoardView.this.mDragColumn.hide();
                BoardView.this.mRootLayout.removeView(BoardView.this.mDragColumn.getDragItemView());
                if (BoardView.this.mBoardListener != null) {
                    BoardView.this.mBoardListener.onColumnDragEnded(BoardView.this.getColumnOfList(BoardView.this.mCurrentRecyclerView));
                }

            }
        });
    }

    private void moveColumn(int fromIndex, int toIndex) {
        DragItemRecyclerView list = (DragItemRecyclerView)this.mLists.remove(fromIndex);
        this.mLists.add(toIndex, list);
        View header = (View)this.mHeaders.remove(fromIndex);
        this.mHeaders.add(toIndex, header);
        final View column1 = this.mColumnLayout.getChildAt(fromIndex);
        final View column2 = this.mColumnLayout.getChildAt(toIndex);
        this.mColumnLayout.removeViewAt(fromIndex);
        this.mColumnLayout.addView(column1, toIndex);
        this.mColumnLayout.addOnLayoutChangeListener(new OnLayoutChangeListener() {
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                BoardView.this.mColumnLayout.removeOnLayoutChangeListener(this);
                column2.setTranslationX(column2.getTranslationX() + (float)column1.getLeft() - (float)column2.getLeft());
                column2.animate().translationX(0.0F).setDuration(350L).start();
            }
        });
        if (this.mBoardListener != null) {
            this.mBoardListener.onColumnDragChangedPosition(fromIndex, toIndex);
        }

    }

    public DragItemRecyclerView insertColumn(DragItemAdapter adapter, int index, @Nullable View header, @Nullable View columnDragView, boolean hasFixedItemSize) {
        DragItemRecyclerView recyclerView = this.insertColumn(adapter, index, header, hasFixedItemSize);
        this.setupColumnDragListener(columnDragView, recyclerView);
        return recyclerView;
    }

    public DragItemRecyclerView addColumn(DragItemAdapter adapter, @Nullable View header, @Nullable View columnDragView, boolean hasFixedItemSize) {
        DragItemRecyclerView recyclerView = this.insertColumn(adapter, this.getColumnCount(), header, hasFixedItemSize);
        this.setupColumnDragListener(columnDragView, recyclerView);
        return recyclerView;
    }

    private void setupColumnDragListener(View columnDragView, final DragItemRecyclerView recyclerView) {
        if (columnDragView != null) {
            columnDragView.setOnLongClickListener(new OnLongClickListener() {
                public boolean onLongClick(View v) {
                    BoardView.this.startDragColumn(recyclerView, BoardView.this.mTouchX, BoardView.this.mTouchY);
                    return true;
                }
            });
        }

    }

    private DragItemRecyclerView insertColumn(DragItemAdapter adapter, int index, @Nullable View header, boolean hasFixedItemSize) {
        if (index > this.getColumnCount()) {
            throw new IllegalArgumentException("Index is out of bounds");
        } else {
            final DragItemRecyclerView recyclerView = (DragItemRecyclerView)LayoutInflater.from(this.getContext()).inflate(layout.drag_item_recycler_view, this, false);
            recyclerView.setId(this.getColumnCount());
            recyclerView.setHorizontalScrollBarEnabled(false);
            recyclerView.setVerticalScrollBarEnabled(false);
            recyclerView.setMotionEventSplittingEnabled(false);
            recyclerView.setDragItem(this.mDragItem);
            recyclerView.setLayoutParams(new android.widget.LinearLayout.LayoutParams(-1, -1));
            recyclerView.setLayoutManager(new LinearLayoutManager(this.getContext()));
            recyclerView.setHasFixedSize(hasFixedItemSize);
            recyclerView.setItemAnimator(new DefaultItemAnimator());
            recyclerView.setDragItemListener(new DragItemListener() {
                public void onDragStarted(int itemPosition, float x, float y) {
                    BoardView.this.mDragStartColumn = BoardView.this.getColumnOfList(recyclerView);
                    BoardView.this.mDragStartRow = itemPosition;
                    BoardView.this.mCurrentRecyclerView = recyclerView;
                    BoardView.this.mDragItem.setOffset(((View)BoardView.this.mCurrentRecyclerView.getParent()).getX(), BoardView.this.mCurrentRecyclerView.getY());
                    if (BoardView.this.mBoardListener != null) {
                        BoardView.this.mBoardListener.onItemDragStarted(BoardView.this.mDragStartColumn, BoardView.this.mDragStartRow);
                    }

                    BoardView.this.invalidate();
                }

                public void onDragging(int itemPosition, float x, float y) {
                    int column = BoardView.this.getColumnOfList(recyclerView);
                    boolean positionChanged = column != BoardView.this.mLastDragColumn || itemPosition != BoardView.this.mLastDragRow;
                    if (BoardView.this.mBoardListener != null && positionChanged) {
                        BoardView.this.mLastDragColumn = column;
                        BoardView.this.mLastDragRow = itemPosition;
                        BoardView.this.mBoardListener.onItemChangedPosition(BoardView.this.mDragStartColumn, BoardView.this.mDragStartRow, column, itemPosition);
                    }

                }

                public void onDragEnded(int newItemPosition) {
                    BoardView.this.mLastDragColumn = -1;
                    BoardView.this.mLastDragRow = -1;
                    BoardView.this.mItemDraggingChanged = false;
                    if (BoardView.this.mBoardListener != null) {
                        BoardView.this.mBoardListener.onItemDragEnded(BoardView.this.mDragStartColumn, BoardView.this.mDragStartRow, BoardView.this.getColumnOfList(recyclerView), newItemPosition);
                    }

                }
            });
            recyclerView.setDragItemCallback(new DragItemCallback() {
                public boolean canDragItemAtPosition(int dragPosition) {
                    int column = BoardView.this.getColumnOfList(recyclerView);
                    return BoardView.this.mBoardCallback == null || BoardView.this.mBoardCallback.canDragItemAtPosition(column, dragPosition);
                }

                public boolean canDropItemAtPosition(int dropPosition) {
                    int column = BoardView.this.getColumnOfList(recyclerView);
                    return BoardView.this.mBoardCallback == null || BoardView.this.mBoardCallback.canDropItemAtPosition(BoardView.this.mDragStartColumn, BoardView.this.mDragStartRow, column, dropPosition);
                }
            });
            recyclerView.setAdapter(adapter);
            recyclerView.setDragEnabled(this.mDragEnabled);
            adapter.setDragStartedListener(new DragStartCallback() {
                public boolean startDrag(View itemView, long itemId) {
                    return recyclerView.startDrag(itemView, itemId, BoardView.this.getRelativeViewTouchX((View)recyclerView.getParent()), BoardView.this.getRelativeViewTouchY(recyclerView));
                }

                public boolean isDragging() {
                    return recyclerView.isDragging();
                }
            });
            LinearLayout layout = new LinearLayout(this.getContext());
            layout.setOrientation(1);
            layout.setLayoutParams(new LayoutParams(this.mColumnWidth, -1));
            View columnHeader = header;
            if (header == null) {
                columnHeader = new View(this.getContext());
                columnHeader.setVisibility(8);
            }

            layout.addView(columnHeader);
            this.mHeaders.add(columnHeader);
            layout.addView(recyclerView);
            this.mLists.add(index, recyclerView);
            this.mColumnLayout.addView(layout, index);
            return recyclerView;
        }
    }

    static class SavedState extends BaseSavedState {
        public int currentColumn;
        public static final Creator<BoardView.SavedState> CREATOR = new Creator<BoardView.SavedState>() {
            public BoardView.SavedState createFromParcel(Parcel in) {
                return new BoardView.SavedState(in);
            }

            public BoardView.SavedState[] newArray(int size) {
                return new BoardView.SavedState[size];
            }
        };

        private SavedState(Parcelable superState, int currentColumn) {
            super(superState);
            this.currentColumn = currentColumn;
        }

        public SavedState(Parcel source) {
            super(source);
            this.currentColumn = source.readInt();
        }

        public int describeContents() {
            return 0;
        }

        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(this.currentColumn);
        }
    }

    private class GestureListener extends SimpleOnGestureListener {
        private float mStartScrollX;
        private int mStartColumn;

        private GestureListener() {
        }

        public boolean onDown(MotionEvent e) {
            this.mStartScrollX = (float)BoardView.this.getScrollX();
            this.mStartColumn = BoardView.this.mCurrentColumn;
            return super.onDown(e);
        }

        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            int closestColumn = BoardView.this.getClosestSnapColumn();
            int newColumn = closestColumn;
            boolean wrongSnapDirection = closestColumn > this.mStartColumn && velocityX > 0.0F || closestColumn < this.mStartColumn && velocityX < 0.0F;
            if (this.mStartScrollX == (float)BoardView.this.getScrollX()) {
                newColumn = this.mStartColumn;
            } else if (this.mStartColumn == closestColumn || wrongSnapDirection) {
                if (velocityX < 0.0F) {
                    newColumn = closestColumn + 1;
                } else {
                    newColumn = closestColumn - 1;
                }
            }

            if (newColumn < 0 || newColumn > BoardView.this.mLists.size() - 1) {
                newColumn = newColumn < 0 ? 0 : BoardView.this.mLists.size() - 1;
            }

            BoardView.this.scrollToColumn(newColumn, true);
            return true;
        }
    }

    public static enum ColumnSnapPosition {
        LEFT,
        CENTER,
        RIGHT;

        private ColumnSnapPosition() {
        }
    }

    public abstract static class BoardListenerAdapter implements BoardView.BoardListener {
        public BoardListenerAdapter() {
        }

        public void onItemDragStarted(int column, int row) {
        }

        public void onItemDragEnded(int fromColumn, int fromRow, int toColumn, int toRow) {
        }

        public void onItemChangedPosition(int oldColumn, int oldRow, int newColumn, int newRow) {
        }

        public void onItemChangedColumn(int oldColumn, int newColumn) {
        }

        public void onFocusedColumnChanged(int oldColumn, int newColumn) {
        }

        public void onColumnDragStarted(int position) {
        }

        public void onColumnDragChangedPosition(int oldPosition, int newPosition) {
        }

        public void onColumnDragEnded(int position) {
        }
    }

    public interface BoardListener {
        void onItemDragStarted(int var1, int var2);

        void onItemDragEnded(int var1, int var2, int var3, int var4);

        void onItemChangedPosition(int var1, int var2, int var3, int var4);

        void onItemChangedColumn(int var1, int var2);

        void onFocusedColumnChanged(int var1, int var2);

        void onColumnDragStarted(int var1);

        void onColumnDragChangedPosition(int var1, int var2);

        void onColumnDragEnded(int var1);
    }

    public interface BoardCallback {
        boolean canDragItemAtPosition(int var1, int var2);

        boolean canDropItemAtPosition(int var1, int var2, int var3, int var4);
    }
}
