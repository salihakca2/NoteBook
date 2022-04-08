package com.salihakca2.notebook

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.icu.text.SimpleDateFormat
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import com.google.android.material.snackbar.Snackbar
import com.salihakca2.notebook.databinding.ActivityAddNoteBinding
import com.salihakca2.notebook.model.Note
import com.salihakca2.notebook.roomDb.NoteDao
import com.salihakca2.notebook.roomDb.NoteDatabase
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.Exception

class AddNoteActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddNoteBinding
    private lateinit var activityResultLauncher: ActivityResultLauncher<Intent> //bunu intent için olusturduk
    private lateinit var permissionLauncher : ActivityResultLauncher<String> //bunu ise izin için
    var selectedBitmap : Bitmap? = null
    private lateinit var db: NoteDatabase //database
    private lateinit var noteDao : NoteDao
    val compositeDisposable = CompositeDisposable()
    var noteFromMain: Note? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddNoteBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        db = Room.databaseBuilder(applicationContext, NoteDatabase::class.java, "Notes")
            .build()

        noteDao = db.noteDao()

        binding.saveButton.isEnabled = false

        registerLauncher()

        val intent = intent
        val info= intent.getStringExtra("info")

        if (info.equals("new")){//menu ekle ise
            binding.baslikText.setText("")
            binding.notText.setText("")
            binding.tarihText.setText("")
            binding.saveButton.visibility = View.VISIBLE
            binding.deleteView.visibility= View.INVISIBLE
            binding.imageView.setImageResource(R.drawable.ic_launcher_foreground)
        }else{
            noteFromMain?.let {
                val noted = Note(it.baslik,it.note,it.image)
                binding.baslikText.setText(noted.baslik)
                binding.notText.setText(noted.note)

                val imaged = noted.image //fotoyu cektik
                val bitmap = BitmapFactory.decodeByteArray(imaged,0,imaged.size)
                binding.imageView.setImageBitmap(bitmap)

                //tarih eklicez
                binding.saveButton.visibility = View.INVISIBLE
                binding.deleteView.visibility = View.VISIBLE
            }
        }

    }
    fun saveClick(view: View){
        //save Button

        val baslikText =binding.baslikText.text.toString()
        val noteText = binding.notText.text.toString()
        val tarihText = binding.tarihText.text.toString()


        if (selectedBitmap != null){
            val smallBitmap = makeSmallerBitmap(selectedBitmap!!, 300)

            val outputStream =ByteArrayOutputStream()
            smallBitmap.compress(Bitmap.CompressFormat.PNG,50,outputStream)
            val byteArray = outputStream.toByteArray() //burada byte'a çevirdik

            try {//burada verilerimizi kaydedeceğiz RoomDataBase ile
                if (baslikText.isNullOrEmpty() && noteText.isNullOrEmpty()){//image eklicez
                    //burada image de kaydediyoruz
                val note =  Note(baslikText,noteText,byteArray)
                    compositeDisposable.add(
                        noteDao.insert(note)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(this::handleResponse)
                    )
                }
            }catch (e: Exception){
                e.printStackTrace()
            }

        }
    }
    private fun handleResponse(){
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }
    fun deleteClick(view: View){
        //delete button
        noteFromMain?.let {
            compositeDisposable.add(
            noteDao.delete(it)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::handleResponse)
            )
        }
    }
    private fun makeSmallerBitmap(image: Bitmap, maximumSize: Int): Bitmap{
        var width = image.width
        var height = image.height

        val bitmapRatio: Double = width.toDouble() / height.toDouble()

        if (bitmapRatio > 1){
            width = maximumSize
            val scaledHeight = width / bitmapRatio
            height = scaledHeight.toInt()

        }else {
            height = maximumSize
            val scaledWidth = height * bitmapRatio
            width =  scaledWidth.toInt()
        }
        return Bitmap.createScaledBitmap(image, width, height, true)
    }
    fun selectImage(view: View){
        //selectImage
        if (ContextCompat.checkSelfPermission(this,Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            if(ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.READ_EXTERNAL_STORAGE)){
                Snackbar.make(view,"Permission Needed For Gallery",Snackbar.LENGTH_INDEFINITE).setAction("Give Permission", View.OnClickListener {
                //request permission    - permissionLauncher
                    permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }).show()
            }else{
                //request permission
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }else{
            val intentToGallery =   Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            //activityResultLauncher
            activityResultLauncher.launch(intentToGallery)
        }
    }
    private fun registerLauncher(){
        activityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult(),
            ActivityResultCallback { result ->
        if (result.resultCode == RESULT_OK){
            val intentFromResult = result.data
            if (intentFromResult != null){
                val imageData = intentFromResult.data
                if (imageData != null){
                    //binding.imageView.setImageURI(imageData)
                    try {
                        if (Build.VERSION.SDK_INT >= 28) {
                            val source = ImageDecoder.createSource(this.contentResolver, imageData)
                            selectedBitmap =ImageDecoder.decodeBitmap(source)
                            binding.imageView.setImageBitmap(selectedBitmap)
                        }else{
                            selectedBitmap = MediaStore.Images.Media.getBitmap(contentResolver,imageData)
                            binding.imageView.setImageBitmap(selectedBitmap)
                        }
                    }catch (e:Exception){
                        e.printStackTrace()
                    }
                }
            }
        }

            })
          permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission(), ActivityResultCallback { result ->
             if (result){
                 val intentToGallery = Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                 activityResultLauncher.launch(intentToGallery)
            }else{
                Toast.makeText(this,"Permission Needed Please", Toast.LENGTH_LONG).show()
        }
        })
      }

    fun tarihYazdir(): String{
        var takvim = Calendar.getInstance().time
        var formatlayici = java.text.SimpleDateFormat("dd/M/yyyy hh:mm:ss", Locale("tr"))
        var tarih = formatlayici.format(takvim)

        return tarih
    }

    override fun onDestroy() {
        super.onDestroy()
        compositeDisposable.clear()  //temizleme
    }
}