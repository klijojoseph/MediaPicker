package com.medialib.mediapicker

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.fxn.pix.Options
import com.fxn.pix.Pix
import com.fxn.utility.PermUtil
import com.medialib.trimmer.utils.FileUtils
import com.theartofdev.edmodo.cropper.CropImage
import com.theartofdev.edmodo.cropper.CropImageActivity
import com.theartofdev.edmodo.cropper.CropImageOptions
import droidninja.filepicker.FilePickerActivity
import droidninja.filepicker.FilePickerConst
import droidninja.filepicker.PickerManager
import java.io.File
import java.io.IOException

import androidx.activity.result.contract.ActivityResultContracts


class MediaPicker : AppCompatActivity() {

    companion object {
        const val TAG_MEDIA_TYPE = "TAG_MEDIA_TYPE"
        const val TAG_CHOOSE_TYPE = "TAG_CHOOSE_TYPE"
        const val TAG_MEDIA_RESULT_PATH = "TAG_MEDIA_RESULT_PATH"
        const val TAG_FILE_NAME = "TAG_FILE_NAME"
        const val TAG_FILE_CONTENT = "TAG_FILE_CONTENT"
        var TYPE_IMAGE = 1
        var TYPE_VIDEO = 2
        var CHOOSER_CAMERA = 3
        var CHOOSER_GALLERY = 4
        const val REQUEST_MEDIA = 3000
        private const val REQUEST_VIDEO = 3001
        private const val REQUEST_IMAGE = 3002
        private const val REQUEST_CHOOSER = 4001

    }


    private val startForResult =
        registerForActivityResult(StartActivityForResult()) { result ->
            when (result.resultCode) {
                RESULT_OK -> {
                        if (mediaType == TYPE_VIDEO) {
                            val uri =
                                result.data?.getParcelableExtra<Uri>(TrimmerActivity.TRIM_VIDEO_RESULT)
                            processFile(uri!!)
                        } else if (mediaType == TYPE_IMAGE) {
                            val cropResult = CropImage.getActivityResult(result.data)
                            processFile(cropResult.getUri())
                        }
                }
                RESULT_CANCELED -> {
                    setResultCancelled()
                }
                else -> {
                }
            }
        }

    private val cameraPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result.entries.all {
                it.value == true
            }

        if (granted) {
            startMediaPicker()
        } else {
            setResultCancelled()
        }
    }

    var mediaType = TYPE_IMAGE
    var chooserType = CHOOSER_CAMERA
    private val returnURLs: ArrayList<String> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_picker)
        mediaType = intent.getIntExtra(TAG_MEDIA_TYPE, TYPE_IMAGE)
        chooserType = intent.getIntExtra(TAG_CHOOSE_TYPE, CHOOSER_CAMERA)
        if(checkPermission())
            startMediaPicker()
    }

    private fun checkPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            !== PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            !== PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            !== PackageManager.PERMISSION_GRANTED
        ) {
            requestPermission()
            false
        } else true
    }

    private fun requestPermission() {
        cameraPermissionRequest.launch(
            arrayOf(Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(checkPermission())
            startMediaPicker()
    }



    private fun startMediaPicker() {
        if (chooserType == CHOOSER_CAMERA) {
            startPixCamera();
        } else if (chooserType == CHOOSER_GALLERY) {
            startFilePick()
        }
    }

    private fun getOptions(): Options? {
        val mode: Options.Mode =
            if (mediaType == TYPE_VIDEO) Options.Mode.Video else Options.Mode.Picture
        return Options.init()
            .setRequestCode(if (mediaType == TYPE_VIDEO) REQUEST_VIDEO else REQUEST_IMAGE) //Request code for activity results
            .setCount(1) //Number of images to restict selection count
            .setFrontfacing(false) //Front Facing camera on start
            .setPreSelectedUrls(returnURLs) //Pre selected Image Urls
            .setSpanCount(4) //Span count for gallery min 1 & max 5
            .setMode(mode) //Option to select only pictures or videos or both
            .setVideoDurationLimitinSeconds(30) //Duration for video recording
            .setScreenOrientation(Options.SCREEN_ORIENTATION_PORTRAIT) //Orientaion
            .setPath("/MediaPicker/images")
    }

    private fun startPixCamera() {
        val startForResult = registerForActivityResult(
            StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK &&
                result.data != null
            ) {
                if (mediaType == TYPE_IMAGE) {
                    val returnValue =
                        result.data!!.getStringArrayListExtra(Pix.IMAGE_RESULTS)
                    if (returnValue!!.size > 0) {
                        startImageTrim(Uri.fromFile(File(returnValue[0])))
                    }
                } else if (mediaType == TYPE_VIDEO) {
                    val returnValue =
                        result.data!!.getStringArrayListExtra(Pix.IMAGE_RESULTS)
                    if (returnValue!!.size > 0) {
                        startVideoTrim(Uri.fromFile(File(returnValue[0])))
                    }
                }
            }
        }
        PermUtil.checkForCamaraWritePermissions(this@MediaPicker) {
            val i = Intent(this@MediaPicker, Pix::class.java)
            i.putExtra("options", getOptions())
            startForResult.launch(i)
        }
    }

    private fun startFilePick() {
        val startForResult = registerForActivityResult(
            StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK &&
                result.data != null
            ) {
                val returnValue =
                    result.data!!.getParcelableArrayListExtra<Uri>(FilePickerConst.KEY_SELECTED_MEDIA)
                if (returnValue!!.size > 0) {
                    if (mediaType == TYPE_IMAGE) {
                        startImageTrim(returnValue[0])

                    } else if (mediaType == TYPE_VIDEO) {
                        startVideoTrim(returnValue[0])
                    }
                }
            }
        }
        val intent = Intent(this@MediaPicker, FilePickerActivity::class.java)
        val bundle = Bundle()
        PickerManager.setMaxCount(1)
        PickerManager.setShowVideos(mediaType == TYPE_VIDEO)
        PickerManager.setShowImages(mediaType == TYPE_IMAGE)
        PickerManager.isEnableCamera = false
        PickerManager.title = if(mediaType== TYPE_VIDEO) "Select a Video" else "Select an Image"
        bundle.putInt(FilePickerConst.EXTRA_PICKER_TYPE, FilePickerConst.MEDIA_PICKER)
        intent.putExtras(bundle)
        startForResult.launch(intent)
    }


    private fun startImageTrim(uri: Uri) {
        val intent = Intent(this, CropImageActivity::class.java)
        val bundle = Bundle()
        bundle.putParcelable(CropImage.CROP_IMAGE_EXTRA_SOURCE, uri)
        bundle.putParcelable(CropImage.CROP_IMAGE_EXTRA_OPTIONS, CropImageOptions())
        intent.putExtra(CropImage.CROP_IMAGE_EXTRA_BUNDLE, bundle)
        startForResult.launch(intent)
    }

    private fun startVideoTrim(uri: Uri) {
        val intent = Intent(this, TrimmerActivity::class.java)
        intent.putExtra(MainActivity.EXTRA_VIDEO_PATH, FileUtils.getPath(this, uri))
        startForResult.launch(intent)
    }


    private fun processFile(uri: Uri) {
        try {
            val file = File(uri.toString())
            val fileName: String =
                file.getAbsolutePath().substring(file.getAbsolutePath().lastIndexOf("/") + 1)
            setResultOk(fileName, uri, file.getAbsolutePath())
        } catch (ex: IOException) {
            ex.printStackTrace()
            setResultOk("", uri, "")
        }
    }


    private fun setResultOk(fileName: String, fileContent: Uri, filePath: String) {
        val intent = Intent()
        intent.putExtra(TAG_FILE_NAME, fileName)
        intent.putExtra(TAG_FILE_CONTENT, fileContent)
        intent.putExtra(TAG_MEDIA_RESULT_PATH, filePath)
        intent.putExtra(TAG_MEDIA_TYPE, mediaType)
        setResult(RESULT_OK, intent)
        finish()
    }

    private fun setResultOk_empty() {
        val intent = Intent()
        intent.putExtra(TAG_MEDIA_RESULT_PATH, "")
        setResult(RESULT_OK, intent)
        finish()
    }

    private fun setResultCancelled() {
        val intent = Intent()
        setResult(RESULT_CANCELED, intent)
        finish()
    }

    class Builder {
        private var mediaType = TYPE_IMAGE
        private var chooseType = CHOOSER_CAMERA
        private var launcher: ActivityResultLauncher<Intent>? = null
        private var intent: Intent? = null
        fun setMediaType(type: Int): Builder {
            mediaType = type
            return this
        }

        fun setChooseType(type: Int): Builder {
            chooseType = type
            return this
        }

        fun start(activity: Activity?, launcher: ActivityResultLauncher<Intent?>?) {
            if (launcher != null) {
                val intent = Intent(activity, MediaPicker::class.java)
                intent.putExtra(TAG_MEDIA_TYPE, mediaType)
                intent.putExtra(TAG_CHOOSE_TYPE, chooseType)
                launcher.launch(intent)
            }
        }

    }

}