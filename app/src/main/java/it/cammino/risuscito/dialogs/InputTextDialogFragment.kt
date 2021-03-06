package it.cammino.risuscito.dialogs


import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.input.getInputField
import com.afollestad.materialdialogs.input.input
import java.io.Serializable

@Suppress("unused")
class InputTextDialogFragment : DialogFragment() {

    private var mCallback: SimpleInputCallback? = null

    private val builder: Builder?
        get() = if (arguments == null || !arguments!!.containsKey("builder")) null else arguments!!.getSerializable("builder") as Builder

    override fun onDestroyView() {
        if (dialog != null && retainInstance)
            dialog.setDismissMessage(null)
        super.onDestroyView()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    @SuppressLint("CheckResult")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val mBuilder = builder
                ?: throw IllegalStateException("SimpleDialogFragment should be created using its Builder interface.")

        if (mCallback == null)
            mCallback = mBuilder.mListener

//        val dialogBuilder = MaterialDialog.Builder(activity!!)
//                .input("", if (mBuilder.mPrefill != null) mBuilder.mPrefill else "", false) { _, _ -> }
//                .autoDismiss(mBuilder.mAutoDismiss)
        val dialog = MaterialDialog(activity!!)
                .input(prefill = if (mBuilder.mPrefill != null) mBuilder.mPrefill else "", inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)

        if (mBuilder.mTitle != 0)
            dialog.title(res = mBuilder.mTitle)

        if (!mBuilder.mAutoDismiss)
            dialog.noAutoDismiss()

        if (mBuilder.mPositiveButton != null) {
            dialog.positiveButton(text = mBuilder.mPositiveButton) { mDialog ->
                Log.d(javaClass.name, "onClick: mCallback " + mCallback!!)
                mCallback!!.onPositive(mBuilder.mTag, mDialog)
            }
        }

        if (mBuilder.mNegativeButton != null) {
            dialog.negativeButton(text = mBuilder.mNegativeButton) { mDialog ->
                Log.d(javaClass.name, "onClick: mCallback " + mCallback!!)
                mCallback!!.onNegative(mBuilder.mTag, mDialog)
            }
        }


//        if (mBuilder.mTitle != 0)
//            dialogBuilder.title(mBuilder.mTitle)

//        if (mBuilder.mPositiveButton != null) {
//            dialogBuilder.positiveText(mBuilder.mPositiveButton!!)
//                    .onPositive(object : MaterialDialog.SingleButtonCallback {
//                        override fun onClick(dialog: MaterialDialog, which: DialogAction) {
//                            Log.d(javaClass.name, "onClick: mCallback " + mCallback!!)
//                            mCallback!!.onPositive(mBuilder.mTag, dialog)
//                        }
//                    })
//        }

//        if (mBuilder.mNegativeButton != null) {
//            dialogBuilder.negativeText(mBuilder.mNegativeButton!!)
//                    .onNegative { dialog, _ -> mCallback!!.onNegative(mBuilder.mTag, dialog) }
//        }

//        dialogBuilder.typeface(ResourcesCompat.getFont(activity!!, R.font.googlesans_medium), ResourcesCompat.getFont(activity!!, R.font.googlesans_regular))

//        val dialog = dialogBuilder.build()

        val mEditText = dialog.getInputField()
        if (mEditText != null) {

            if (mBuilder.mPrefill != null)
                mEditText.selectAll()

//            mEditText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

        }

        dialog.setCancelable(mBuilder.mCanceable)

        dialog.setOnKeyListener(DialogInterface.OnKeyListener
        { arg0, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                arg0.cancel()
                return@OnKeyListener true
            }
            false
        })

        return dialog
    }

    fun setmCallback(callback: SimpleInputCallback) {
        mCallback = callback
    }

    fun cancel() {
        dialog.cancel()
    }

    fun setOnCancelListener(listener: DialogInterface.OnCancelListener) {
        dialog.setOnCancelListener(listener)
    }

    class Builder(context: AppCompatActivity, @field:Transient var mListener: SimpleInputCallback, val mTag: String) : Serializable {

        @Transient
        private val mContext: AppCompatActivity = context
        @StringRes
        var mTitle = 0
        var mPositiveButton: CharSequence? = null
        var mNegativeButton: CharSequence? = null
        var mCanceable = false
        var mAutoDismiss = true
        var mPrefill: CharSequence? = null

        fun title(@StringRes text: Int): Builder {
            mTitle = text
            return this
        }

        fun prefill(@StringRes text: Int): Builder {
            mPrefill = this.mContext.resources.getText(text)
            return this
        }

        fun prefill(text: String): Builder {
            mPrefill = text
            return this
        }

        fun positiveButton(@StringRes text: Int): Builder {
            mPositiveButton = this.mContext.resources.getText(text)
            return this
        }

        fun negativeButton(@StringRes text: Int): Builder {
            mNegativeButton = this.mContext.resources.getText(text)
            return this
        }

        fun setCanceable(canceable: Boolean): Builder {
            mCanceable = canceable
            return this
        }

        fun setAutoDismiss(autoDismiss: Boolean): Builder {
            mAutoDismiss = autoDismiss
            return this
        }

        fun build(): InputTextDialogFragment {
            val dialog = InputTextDialogFragment()
            val args = Bundle()
            args.putSerializable("builder", this)
            dialog.arguments = args
            return dialog
        }

        fun show(): InputTextDialogFragment {
            val dialog = build()
            dialog.show(mContext)
            return dialog
        }
    }

    private fun dismissIfNecessary(context: AppCompatActivity, tag: String) {
        val frag = context.supportFragmentManager.findFragmentByTag(tag)
        if (frag != null) {
            (frag as DialogFragment).dismiss()
            context.supportFragmentManager.beginTransaction()
                    .remove(frag).commit()
        }
    }

    fun show(context: AppCompatActivity): InputTextDialogFragment {
        val builder = builder
        if (builder != null) {
            dismissIfNecessary(context, builder.mTag)
            show(context.supportFragmentManager, builder.mTag)
        }
        return this
    }

    interface SimpleInputCallback {
        fun onPositive(tag: String, dialog: MaterialDialog)
        fun onNegative(tag: String, dialog: MaterialDialog)
        fun onNeutral(tag: String, dialog: MaterialDialog)
    }

    companion object {

        fun findVisible(context: AppCompatActivity, tag: String): InputTextDialogFragment? {
            val frag = context.supportFragmentManager.findFragmentByTag(tag)
            return if (frag != null && frag is InputTextDialogFragment) frag else null
        }
    }

}
