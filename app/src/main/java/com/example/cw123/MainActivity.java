package com.example.cw123;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;



import java.util.ArrayList;
import com.squareup.picasso.Picasso;


import android.widget.Button;
import android.widget.EditText;

import java.io.OutputStreamWriter;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;



import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    protected static final String _Filename ="save_image_link_list.txt";

    protected ImageView image;
    protected ImageButton imageButtonNext,imageButtonPre;

    protected ArrayList<String> _imgList;
    protected int _index;
    protected int _itemCounter;

    protected EditText txtLink;
    protected Button buttonAdd;

    protected ImageButton imageButtonCamera;
    protected TextView tvDescription;

    //Camera
    protected static final int REQUEST_CODE_CAMERA = 100;
    protected static final int REQUEST_CODE_PERMISSIONS_CAMERA = 125;
    protected static final String[] REQUIRED_PERMISSIONS_CAMERA = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };


    //  GPS.
    protected final int LOCATION_REFRESH_TIME = 15000; // 15 seconds to update.
    protected final int LOCATION_REFRESH_DISTANCE = 500; // 500 meters to update.
    protected final int REQUEST_CODE_PERMISSIONS_GPS = 105;
    protected final String[] REQUIRED_PERMISSIONS_GPS = new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    protected Location currentLocation;
    protected LocationManager locationManager;
    protected LocationListener locationListener;


    protected boolean allPermissionsGranted_CAMERA() {
        for (String permission : REQUIRED_PERMISSIONS_CAMERA)
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED)
                return false;

        return true;
    }
    private boolean allPermissionsGranted_GPS() {
        for (String permission : REQUIRED_PERMISSIONS_GPS)
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED)
                return false;

        return true;
    }

    protected Uri saveImage(Bitmap bitmap) {
        String fileName = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(new Date());
        String path = Environment.DIRECTORY_PICTURES + File.separator + "CW2";

        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, path);
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");

        ContentResolver resolver = getContentResolver();
        Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);

        try (ParcelFileDescriptor parcelFileDescriptor = resolver.openFileDescriptor(imageUri, "w")) {
            FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();

            try (OutputStream stream = new FileOutputStream(fileDescriptor)) {
                // Perform operations on "stream".
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            }

            // Sync data with disk. It's mandatory to be able later to call writeExif.
            fileDescriptor.sync();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Saving Image FAILED.", Toast.LENGTH_SHORT).show();
            return null;
        }

        return imageUri;
    }


    // Write details to images.
    protected void writeExif(Uri uri) {
        try (ParcelFileDescriptor imagePfd = getContentResolver().openFileDescriptor(uri, "rw")) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                ExifInterface exif = new ExifInterface(imagePfd.getFileDescriptor());

                exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, new Date().toString());

                if (null != currentLocation) {
                    exif.setAttribute(ExifInterface.TAG_GPS_DEST_LATITUDE_REF, String.valueOf(currentLocation.getLatitude()));
                    exif.setAttribute(ExifInterface.TAG_GPS_DEST_LONGITUDE_REF, String.valueOf(currentLocation.getLongitude()));
                }

                exif.saveAttributes();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    protected void takePicture() {
        // Ask for camera permissions.
        if (!allPermissionsGranted_CAMERA()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS_CAMERA, REQUEST_CODE_PERMISSIONS_CAMERA);
            return;
        }

        Intent imageCaptureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        Intent fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
        fileIntent.setType("image/*");

        Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryIntent.setType("image/*");

        Intent chooserIntent = Intent.createChooser(imageCaptureIntent, "Select Image");
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{fileIntent, galleryIntent});

        startActivityForResult(chooserIntent, REQUEST_CODE_CAMERA);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_CAMERA) {
            if (resultCode == Activity.RESULT_OK && null != data) {
                Uri uri = data.getData();

                if (null == uri) {
                    Bitmap bitmap = (Bitmap) data.getExtras().get("data");
                    uri = saveImage(bitmap);
                    writeExif(uri);
                }
                _imgList.add(uri.toString());
                _index = _imgList.size() - 1;
                loadImage();

                return;
            }

            Toast.makeText(this, "Select Image Failed.", Toast.LENGTH_SHORT).show();
        }
    }

    protected void startGPS() {
        // Check permissions.
        if (!allPermissionsGranted_GPS()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS_GPS, REQUEST_CODE_PERMISSIONS_GPS);
            return;
        }

        locationListener = location -> {
            currentLocation = location;
            Toast.makeText(MainActivity.this, "Get current location successfully.", Toast.LENGTH_SHORT).show();
        };

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, LOCATION_REFRESH_TIME, LOCATION_REFRESH_DISTANCE, locationListener);
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        image = findViewById(R.id.imageView);
        imageButtonNext = findViewById(R.id.imageButtonNext);
        imageButtonPre = findViewById(R.id.imageButtonPre);


        imageButtonPre.setOnClickListener(v -> preimg());
        imageButtonNext.setOnClickListener(v -> nextimg());


        _imgList = getImageList();
        _index = 1;


        txtLink=findViewById(R.id.txtLink);


        buttonAdd = findViewById(R.id.buttonAdd);
        buttonAdd.setOnClickListener(v -> addImage());


        imageButtonCamera = findViewById(R.id.imageButtonCamera);
        imageButtonCamera.setOnClickListener(v -> takePicture());

        tvDescription = findViewById(R.id.tvDescription);


        startGPS();
        loadImage();
    }

    protected ArrayList<String> getImageList() {
        ArrayList<String> imgList = new ArrayList<>();

        imgList.add("https://media.nu.nl/m/odoxxvhaqiru_sqr256.jpg/arsenal-verslaat-liverpool-eindelijk-weer-en-blijft-city-voor-in-premier-league.png");
        imgList.add("https://cdn-1.webcatalog.io/catalog/premier-league/premier-league-icon-filled.png");
        imgList.add("https://media.nu.nl/m/yahx5p4azgfm_sqr256.jpg/haaland-maakt-bij-premier-league-debuut-direct-het-verschil-voor-manchester-city.png");

        getImageListFromFile(imgList);

        Toast.makeText(this, "Get Image List Successfully", Toast.LENGTH_SHORT).show();

        return imgList;
    }


    protected void loadImage() {
        _itemCounter = _imgList.size();
        Uri imageUri = Uri.parse(_imgList.get(_index));
        image.setImageURI(imageUri);

        try {

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {

                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                ExifInterface exifInterface = new ExifInterface(inputStream);

                String tag_address = "Not Found";

                String tag_date = exifInterface.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
                String tag_latitude = exifInterface.getAttribute(ExifInterface.TAG_GPS_DEST_LATITUDE_REF);
                String tag_longitude = exifInterface.getAttribute(ExifInterface.TAG_GPS_DEST_LONGITUDE_REF);

                if (tag_longitude != null && !tag_longitude.isEmpty() &&
                        tag_latitude != null && !tag_latitude.isEmpty()) {
                    double latitude = Double.parseDouble(tag_latitude);
                    double longitude = Double.parseDouble(tag_longitude);

                    Geocoder gcd = new Geocoder(getBaseContext(), Locale.getDefault());
                    List<Address> addresses = gcd.getFromLocation(latitude, longitude, 1);

                    if (addresses.size() > 0) {
                        tag_address = addresses.get(0).getAddressLine(0);
                    }
                }

                String description = "Taken on " + tag_date + " at " + tag_address + ".";


                    tvDescription.setText(description);


            }
        } catch (IOException e) {
            e.printStackTrace();
        }



        Picasso.with(this)
                .load(_imgList.get(_index))
                .into(image);
        Picasso.with(this).setLoggingEnabled(true);
        imageButtonPre.setVisibility(View.VISIBLE);
        imageButtonNext.setVisibility(View.VISIBLE);



        if (_index == 0){
            Picasso.with(this)
                    .load(_imgList.get(_index))
                    .into(image);
            Picasso.with(this).setLoggingEnabled(true);
            imageButtonPre.setVisibility(View.INVISIBLE);
            imageButtonNext.setVisibility(View.VISIBLE);




        }
        if (_index == _itemCounter-1){
            Picasso.with(this)
                    .load(_imgList.get(_index))
                    .into(image);
            Picasso.with(this).setLoggingEnabled(true);
            imageButtonPre.setVisibility(View.VISIBLE);
            imageButtonNext.setVisibility(View.INVISIBLE);



        }






    }


    protected void addImage() {
       String imagelink = txtLink.getText().toString();
       _imgList.add(imagelink);
       writeLinkToFile(imagelink);

        Toast.makeText(this, "Successfully added the image", Toast.LENGTH_SHORT).show();
       txtLink.setText("");

       _index = _itemCounter;
        loadImage();

    }



    protected void preimg() {
        --_index;
        loadImage();
    }
    protected void nextimg(){
        ++_index;
        loadImage();

    }


    protected void writeLinkToFile(String url) {
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(openFileOutput(_Filename, MODE_APPEND));
            outputStreamWriter.write(url);
            outputStreamWriter.append("\r\n");
            outputStreamWriter.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Toast.makeText(this, "File not found.", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void getImageListFromFile(ArrayList<String> imageList) {
        try {
            InputStream inputStream = openFileInput(_Filename);

            if (inputStream != null) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

                String url = "";
                while ((url = bufferedReader.readLine()) != null) {
                    imageList.add(url);
                }

                inputStream.close();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Toast.makeText(this, "File is not found.", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }





}