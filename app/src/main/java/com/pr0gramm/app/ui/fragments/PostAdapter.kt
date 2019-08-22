package com.pr0gramm.app.ui.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.get
import androidx.core.view.size
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.feed.FeedItem
import com.pr0gramm.app.orm.Vote
import com.pr0gramm.app.ui.DelegateAdapter
import com.pr0gramm.app.ui.ListItemTypeAdapterDelegate
import com.pr0gramm.app.ui.staticLayoutAdapterDelegate
import com.pr0gramm.app.ui.views.CommentPostLine
import com.pr0gramm.app.ui.views.InfoLineView
import com.pr0gramm.app.ui.views.PostActions
import com.pr0gramm.app.ui.views.TagsView
import com.pr0gramm.app.util.LongSparseArray
import com.pr0gramm.app.util.dip2px
import com.pr0gramm.app.util.removeFromParent
import com.pr0gramm.app.util.weakref

@Suppress("NOTHING_TO_INLINE")
private inline fun idInCategory(cat: Long, idOffset: Long = 0): Long {
    return (idOffset shl 8) or cat
}

class PostAdapter
    : DelegateAdapter<PostAdapter.Item>(ItemCallback(), name = "PostAdapter") {

    init {
        setHasStableIds(true)

        delegates += CommentItemAdapterDelegate
        delegates += InfoLineItemAdapterDelegate
        delegates += TagsViewHolderAdapterDelegate
        delegates += CommentPostLineAdapterDelegate
        delegates += PlaceholderItemAdapterDelegate
        delegates += staticLayoutAdapterDelegate(R.layout.comments_are_loading, Item.CommentsLoadingItem)
        delegates += staticLayoutAdapterDelegate(R.layout.comments_load_err, Item.LoadErrorItem)
        delegates += staticLayoutAdapterDelegate(R.layout.comments_item_deleted, Item.PostIsDeletedItem)
        delegates += staticLayoutAdapterDelegate(R.layout.comments_no_account, Item.NoCommentsWithoutAccount)
    }

    override fun getItemId(position: Int): Long {
        return items[position].id
    }

    sealed class Item(val id: Long) {
        class PlaceholderItem(val height: Int, val viewer: View, val mediaControlsContainer: View?)
            : Item(idInCategory(0)) {

            override fun hashCode(): Int = height
            override fun equals(other: Any?): Boolean = other is PlaceholderItem && other.height == height
        }

        data class InfoItem(val item: FeedItem, val vote: Vote, val isOurPost: Boolean, val actions: PostActions)
            : Item(idInCategory(1))

        data class TagsItem(val tags: List<Api.Tag>, val votes: LongSparseArray<Vote>, val actions: PostActions)
            : Item(idInCategory(2))

        data class CommentInputItem(val itemId: Long, val actions: PostActions)
            : Item(idInCategory(3))

        object CommentsLoadingItem
            : Item(idInCategory(4))

        object LoadErrorItem
            : Item(idInCategory(5))

        object PostIsDeletedItem
            : Item(idInCategory(6))

        object NoCommentsWithoutAccount
            : Item(idInCategory(7))

        data class CommentItem(val commentTreeItem: CommentTree.Item, val listener: CommentView.Listener)
            : Item(idInCategory(8, commentTreeItem.comment.id))
    }

    private class ItemCallback : DiffUtil.ItemCallback<Item>() {
        override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean {
            return oldItem.id == newItem.id
        }

        @SuppressLint("DiffUtilEquals")
        override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean {
            return oldItem == newItem
        }
    }
}

private object CommentItemAdapterDelegate
    : ListItemTypeAdapterDelegate<PostAdapter.Item.CommentItem, CommentView>() {

    override fun onCreateViewHolder(parent: ViewGroup): CommentView {
        return CommentView(parent)
    }

    override fun onBindViewHolder(holder: CommentView, value: PostAdapter.Item.CommentItem) {
        holder.set(value.commentTreeItem, value.listener)
    }
}

private object TagsViewHolderAdapterDelegate
    : ListItemTypeAdapterDelegate<PostAdapter.Item.TagsItem, TagsViewHolderAdapterDelegate.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(TagsView(parent.context))
    }

    override fun onBindViewHolder(holder: ViewHolder, value: PostAdapter.Item.TagsItem) {
        holder.tagsView.actions = value.actions
        holder.tagsView.updateTags(value.tags, value.votes)
    }

    private class ViewHolder(val tagsView: TagsView) : RecyclerView.ViewHolder(tagsView)
}


private object InfoLineItemAdapterDelegate
    : ListItemTypeAdapterDelegate<PostAdapter.Item.InfoItem, InfoLineItemAdapterDelegate.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(InfoLineView(parent.context))
    }

    override fun onBindViewHolder(holder: ViewHolder, value: PostAdapter.Item.InfoItem) {
        // display the feed item in the view
        holder.infoView.setFeedItem(value.item, value.isOurPost, value.vote)
        holder.infoView.onDetailClickedListener = value.actions
    }

    private class ViewHolder(val infoView: InfoLineView) : RecyclerView.ViewHolder(infoView) {
        init {
            infoView.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }
}


private object CommentPostLineAdapterDelegate
    : ListItemTypeAdapterDelegate<PostAdapter.Item.CommentInputItem, CommentPostLineAdapterDelegate.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(CommentPostLine(parent.context))
    }

    override fun onBindViewHolder(holder: ViewHolder, value: PostAdapter.Item.CommentInputItem) {
        holder.set(value)
    }

    private class ViewHolder(val line: CommentPostLine) : RecyclerView.ViewHolder(line) {
        init {
            line.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        fun set(item: PostAdapter.Item.CommentInputItem) {
            line.updateItemId(item.itemId)

            line.onPostCommentClicked = { text ->
                if (item.actions.writeCommentClicked(text)) {
                    line.clear()
                }
            }
        }
    }
}

private object PlaceholderItemAdapterDelegate
    : ListItemTypeAdapterDelegate<PostAdapter.Item.PlaceholderItem, PlaceholderItemAdapterDelegate.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(PlaceholderView(parent.context))
    }

    override fun onBindViewHolder(holder: ViewHolder, value: PostAdapter.Item.PlaceholderItem) {
        holder.set(value)
    }

    private class ViewHolder(val pv: PlaceholderView) : RecyclerView.ViewHolder(pv) {
        fun set(item: PostAdapter.Item.PlaceholderItem) {
            pv.viewer = item.viewer
            pv.fixedHeight = item.height

            pv.requestLayout()

            // remove all views we do not expect
            for (idx in pv.size - 1 downTo 0) {
                if (pv[idx] !== item.mediaControlsContainer) {
                    pv.removeViewAt(idx)
                }
            }

            if (item.mediaControlsContainer != null) {
                // only move media controls if they are attached to a different placeholder view.
                // the reason to do so is that we could just have received an update after the
                // controls were attached to a player in fullscreen.
                if (pv.parent !== pv && (pv.parent == null || pv.parent is PlaceholderView)) {
                    item.mediaControlsContainer.removeFromParent()
                    pv.addView(item.mediaControlsContainer)
                }

            } else {
                // clear already bound media controlls
                pv.removeAllViews()
            }
        }
    }
}


@SuppressLint("ViewConstructor")
private class PlaceholderView(context: Context) : FrameLayout(context) {
    var viewer by weakref<View?>(null)
    var fixedHeight = context.dip2px(150)

    init {
        val v = View(context)
        v.setBackgroundResource(R.drawable.dropshadow_reverse)

        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dip2px(8))
        lp.gravity = Gravity.BOTTOM
        v.layoutParams = lp

        addView(v)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, fixedHeight)

        measureChildren(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(fixedHeight, MeasureSpec.EXACTLY))
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return viewer?.onTouchEvent(event) ?: false
    }
}
