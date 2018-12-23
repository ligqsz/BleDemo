package com.young.ble.demo

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.text.Layout
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.w3c.dom.Text

/**
 * @auth young
 * @date 2018 2018/12/22 16:03
 */
class DevideAdapter(
    private val context: Context,
    private val layoutId: Int,
    private var dataList: MutableList<String>
) : RecyclerView.Adapter<BlViewHolder>() {
    constructor(context: Context, dataList: MutableList<String>) : this(
        context,
        android.R.layout.simple_expandable_list_item_1,
        dataList
    )

    override fun onCreateViewHolder(p0: ViewGroup, p1: Int): BlViewHolder =
        BlViewHolder(LayoutInflater.from(context).inflate(layoutId, null, false))

    override fun getItemCount(): Int = dataList.size

    override fun onBindViewHolder(p0: BlViewHolder, p1: Int) {
        p0.setText(android.R.id.text1, dataList[p1])
    }
}

class BlViewHolder(private val view: View) : RecyclerView.ViewHolder(view) {
    fun setText(id: Int, msg: String) {
        view.findViewById<TextView>(id).text = msg
    }

    fun getView(id: Int): View = view.findViewById(id)
}