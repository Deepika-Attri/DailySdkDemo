package com.dailysdkdemo.ui.fragments

import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import co.daily.model.Participant
import com.dailysdkdemo.ui.adapters.MainAdapter
import com.dailysdkdemo.data.modelclasses.AllParticipants
import com.dailysdkdemo.ui.callbacks.ClickCallBack
import com.dailysdkdemo.databinding.FragmentPagerBinding

class PagerFragment : Fragment() {

    private lateinit var mainAdapter: MainAdapter
    private var _binding: FragmentPagerBinding? = null
    private val binding get() = _binding!!
    private var list: ArrayList<AllParticipants> = ArrayList()
    private lateinit var clickCallBack: ClickCallBack

    @Suppress("UNCHECKED_CAST")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {

        _binding = FragmentPagerBinding.inflate(inflater, container, false)

        Handler(Looper.getMainLooper()).post {
            val height =
                if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                    _binding?.frameLayout?.measuredHeight
                } else {
                    binding.frameLayout.measuredWidth
                }
            val participantsList =
                requireArguments().getSerializable(EXTRA_TITLE) as ArrayList<Participant>
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                _binding?.rv?.layoutManager =
                    LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
            } else {
                _binding?.rv?.layoutManager =
                    LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false)
            }
            mainAdapter = MainAdapter(requireActivity(), participantsList, height!!, clickCallBack)
            _binding?.rv?.adapter = mainAdapter
            _binding?.rv?.setHasFixedSize(false)

            // to prevent: unable to marshal value error when onPause method is called
            requireArguments().remove(EXTRA_TITLE)

//            setHeightForLandscape(participantsList)

            _binding?.frameLayout?.setOnClickListener {
                clickCallBack.onClick()
            }
        }

        return binding.root
    }

    fun setValue(updatedList: ArrayList<Participant>) {
        if (this::mainAdapter.isInitialized) {
            mainAdapter.setItems(updatedList)
        }
//        setHeightForLandscape(updatedList)
    }

//    private fun setHeightForLandscape(list: ArrayList<Participant>) {
//        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
//            binding.rv.doOnLayout {
//                val param = it.layoutParams as ViewGroup.MarginLayoutParams
//                when (list.size) {
//                    3 -> {
//                        param.setMargins(0, 60, 0, 60)
//                    }
//                    2 -> {
//                        param.setMargins(0, 40, 0, 40)
//                    }
//                    else -> {
//                        param.setMargins(0, 0, 0, 0)
//                    }
//                }
//                it.layoutParams = param
//            }
//        }
//    }

    companion object {

        private const val EXTRA_TITLE = "title"

        fun newInstance(
            items: ArrayList<AllParticipants>,
            position: Int,
            mClickCallBack: ClickCallBack
        ): PagerFragment {
            return PagerFragment().apply {
                list = items
                clickCallBack = mClickCallBack
                arguments = Bundle(1).apply {
                    putSerializable(EXTRA_TITLE, items[position].participant)
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}