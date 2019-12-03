/*
 * Copyright 2014 Magnus Woxblom
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.woxthebox.draglistview.sample;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.util.Pair;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.woxthebox.draglistview.DragItemVertical;
import com.woxthebox.draglistview.DragItemVerticalAdapter;
import java.util.ArrayList;

class ItemAdapter extends DragItemVerticalAdapter<Pair<Long, String>, RecyclerView.ViewHolder> {
    private static int ITEM1 = 0;
    private static int ITEM2 = 1;

    private int mLayoutId;
    private int mGrabHandleId;
    private boolean mDragOnLongPress;

    ItemAdapter(ArrayList<Pair<Long, String>> list, int layoutId, int grabHandleId,
            boolean dragOnLongPress) {
        mLayoutId = layoutId;
        mGrabHandleId = grabHandleId;
        mDragOnLongPress = dragOnLongPress;
        setItemList(list);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == ITEM1) {
            View view = LayoutInflater.from(parent.getContext()).inflate(mLayoutId, parent, false);
            return new ViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(mLayoutId, parent, false);
            return new ViewHolder2(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        if (holder instanceof ViewHolder) {
            String text = mItemList.get(position).second;
            ((ViewHolder) holder).mText.setText(text);
            if (text.equals("con")) {
                FrameLayout.LayoutParams params =
                        (FrameLayout.LayoutParams) ((ViewHolder) holder).mContainer
                                .getLayoutParams();
                params.setMargins(100, 0, 0, 0);
                ((ViewHolder) holder).mContainer.setLayoutParams(params);
            } else {
                FrameLayout.LayoutParams params =
                        (FrameLayout.LayoutParams) ((ViewHolder) holder).mContainer
                                .getLayoutParams();
                params.setMargins(0, 0, 0, 0);
                ((ViewHolder) holder).mContainer.setLayoutParams(params);
            }
            holder.itemView.setTag(mItemList.get(position));
        } else {
            ((ViewHolder2) holder).bind(mItemList.get(position));
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (mItemList.get(position).second.equals("group")) {
            return ITEM2;
        }
        return ITEM1;
    }

    @Override
    public long getUniqueItemId(int position) {
        return mItemList.get(position).first;
    }

    public void updateChildTask(int position) {
        if (mItemList.size() - 1 >= position && position >= 0) {
            long id = mItemList.get(position).first;
            mItemList.remove(position);
            mItemList.add(position, new Pair(id, "con"));
            notifyDataSetChanged();
        }
    }

    public void updateParentTask(int position) {
        if (mItemList.size() - 1 >= position && position >= 0) {
            long id = mItemList.get(position).first;
            mItemList.remove(position);
            mItemList.add(position, new Pair(id, "cha"));
            notifyDataSetChanged();
        }
    }

    class ViewHolder extends DragItemVerticalAdapter.ViewHolder {
        Context mContext;
        TextView mText;
        View mContainer;

        ViewHolder(final View itemView) {
            super(itemView, mGrabHandleId, mDragOnLongPress);
            mText = (TextView) itemView.findViewById(R.id.text);
            mContainer = (View) itemView.findViewById(R.id.card);
            mContext = itemView.getContext();
        }

        @Override
        public void onItemClicked(View view) {
            Toast.makeText(view.getContext(), "Item clicked", Toast.LENGTH_SHORT).show();
        }

        @Override
        public boolean onItemLongClicked(View view) {
            Toast.makeText(view.getContext(), "Item long clicked", Toast.LENGTH_SHORT).show();
            return true;
        }
    }

    class ViewHolder2 extends DragItemVerticalAdapter.ViewHolder {
        Context mContext;
        TextView mText;
        View mContainer;

        public ViewHolder2(View itemView) {
            super(itemView, mGrabHandleId, mDragOnLongPress);
            mText = (TextView) itemView.findViewById(R.id.text);
            mContainer = (View) itemView.findViewById(R.id.card);
            mContext = itemView.getContext();
        }

        public void bind(Pair<Long, String> longStringPair) {
            mText.setText(longStringPair.second);
        }
    }
}
