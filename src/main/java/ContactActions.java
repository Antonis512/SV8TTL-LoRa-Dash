package org.overmesh.android;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** User-initiated contact pickers. No address-book writes or broad contacts permission. */
final class ContactActions {
    private static final int CONTACT=520,PHOTO=521,EXPORT=522,IMPORT=523;
    interface Callback {void result(String event,Object data);}
    private final Activity activity;
    private final Callback callback;
    private final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private String target="",exportJson="";
    private int pending;
    private boolean closed;
    ContactActions(Activity activity,Callback callback){this.activity=activity;this.callback=callback;}
    void close(){closed=true;worker.shutdown();}
    private void emit(String event,Object value){activity.runOnUiThread(()->{if(!closed)callback.result(event,value);});}
    private void error(Exception e){emit("onContactError",e.getMessage()==null?"Contact action failed":e.getMessage());}
    private void pick(Intent intent,int request,String token) {
        if(pending!=0){emit("onContactError","Finish the open picker first");return;}
        target=token;pending=request;
        try{activity.startActivityForResult(intent,request);}catch(Exception e){pending=0;error(e);}
    }
    void pickContact(String id){pick(new Intent(Intent.ACTION_PICK,ContactsContract.Contacts.CONTENT_URI),CONTACT,id);}
    void pickPhoto(String token){pick(new Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"),PHOTO,token);}
    void exportContacts(String json){
        if(pending!=0){emit("onContactError","Finish the open picker first");return;}
        exportJson=json;pick(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"SV8TTL-contacts.json"),EXPORT,"");
    }
    void importContacts(){pick(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json"),IMPORT,"");}
    void communicate(String kind,String value) {
        try {
            if(value==null||value.length()>1000||value.contains("\n")||value.contains("\r"))throw new IllegalArgumentException("Invalid contact address");
            Intent intent;
            switch(kind){
                case "call":intent=new Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",value,null));break;
                case "sms":intent=new Intent(Intent.ACTION_SENDTO,Uri.fromParts("smsto",value,null));break;
                case "email":intent=new Intent(Intent.ACTION_SENDTO,Uri.fromParts("mailto",value,null));break;
                default:throw new IllegalArgumentException("Unknown contact action");
            }
            activity.startActivity(intent);
        }catch(Exception e){error(e);}
    }
    void share(String text,String photo){try{
        Intent intent=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,text);
        if(photo!=null&&!photo.isEmpty()){
            if(!photo.startsWith("data:image/jpeg;base64,")||photo.length()>400000)throw new IllegalArgumentException("Invalid photo");
            java.io.File directory=new java.io.File(activity.getCacheDir(),"contacts-share");if(!directory.isDirectory()&&!directory.mkdirs())throw new IllegalArgumentException("Cannot prepare photo");
            java.io.File file=new java.io.File(directory,java.util.UUID.randomUUID()+".jpg");
            try(OutputStream out=new java.io.FileOutputStream(file)){out.write(Base64.decode(photo.substring(photo.indexOf(',')+1),Base64.DEFAULT));}
            Uri uri=androidx.core.content.FileProvider.getUriForFile(activity,"org.overmesh.android.updates",file);
            intent.setType("image/jpeg").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(android.content.ClipData.newRawUri("Profile photo",uri));
        }
        activity.startActivity(Intent.createChooser(intent,"Share selected contact fields"));
    }catch(Exception e){error(e);}}
    boolean result(int request,int result,Intent data) {
        if(request<CONTACT||request>IMPORT)return false;
        if(request!=pending)return true;
        pending=0;final String token=target,payload=exportJson;exportJson="";
        if(result!=Activity.RESULT_OK||data==null||data.getData()==null)return true;
        Uri uri=data.getData();
        worker.execute(()->{try{
            if(request==CONTACT)emit("onAndroidContact",new JSONObject().put("target",token).put("contact",readContact(uri)));
            else if(request==PHOTO){try(InputStream in=activity.getContentResolver().openInputStream(uri)){emit("onContactPhoto",new JSONObject().put("token",token).put("photo",thumbnail(readBytes(in,20000000))));}}
            else if(request==EXPORT){try(OutputStream out=activity.getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IllegalArgumentException("Cannot open export file");out.write(payload.getBytes(StandardCharsets.UTF_8));}emit("onContactNotice","Contacts exported");}
            else {JSONObject file;try(InputStream in=activity.getContentResolver().openInputStream(uri)){file=new JSONObject(new String(readBytes(in,50000000),StandardCharsets.UTF_8));}
                if(!file.optString("format").equals("sv8ttl-contacts")||file.optInt("version")!=1)throw new IllegalArgumentException("Choose a Contacts JSON export");
                JSONArray items=file.getJSONArray("items");if(items.length()>10000)throw new IllegalArgumentException("Too many contacts");
                for(int i=0;i<items.length();i++)ContactsStore.validate(items.getJSONObject(i));emit("onContactImport",items);
            }
        }catch(Exception e){error(e);}});
        return true;
    }
    private static byte[] readBytes(InputStream in,int limit)throws Exception{
        if(in==null)throw new IllegalArgumentException("Cannot read selected file");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
        while((n=in.read(buf))!=-1){if(out.size()+n>limit)throw new IllegalArgumentException("Selected file is too large");out.write(buf,0,n);}return out.toByteArray();
    }
    private static String thumbnail(byte[] bytes)throws Exception{
        BitmapFactory.Options opt=new BitmapFactory.Options();opt.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,opt);
        if(opt.outWidth<=0||opt.outHeight<=0)throw new IllegalArgumentException("Unsupported photo");
        opt.inSampleSize=1;while(Math.max(opt.outWidth,opt.outHeight)/opt.inSampleSize>768)opt.inSampleSize*=2;
        opt.inJustDecodeBounds=false;Bitmap image=BitmapFactory.decodeByteArray(bytes,0,bytes.length,opt);if(image==null)throw new IllegalArgumentException("Cannot decode photo");
        float scale=Math.min(1f,384f/Math.max(image.getWidth(),image.getHeight()));Bitmap small=Bitmap.createScaledBitmap(image,Math.max(1,Math.round(image.getWidth()*scale)),Math.max(1,Math.round(image.getHeight()*scale)),true);
        ByteArrayOutputStream out=new ByteArrayOutputStream();small.compress(Bitmap.CompressFormat.JPEG,82,out);if(small!=image)small.recycle();image.recycle();
        return "data:image/jpeg;base64,"+Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);
    }
    private String field(Cursor c,String name){int at=c.getColumnIndex(name);return at<0||c.isNull(at)?"":c.getString(at);}
    private JSONObject readContact(Uri uri)throws Exception {
        JSONObject out=new JSONObject();JSONArray phones=new JSONArray(),emails=new JSONArray(),addresses=new JSONArray(),notes=new JSONArray();
        try(Cursor c=activity.getContentResolver().query(uri,null,null,null,null)){
            if(c==null||!c.moveToFirst())throw new IllegalArgumentException("Contact is no longer available");
            out.put("displayName",field(c,ContactsContract.Contacts.DISPLAY_NAME));
            out.put("androidLink",new JSONObject().put("uri",uri.toString()).put("lookup",field(c,ContactsContract.Contacts.LOOKUP_KEY)).put("refreshed",System.currentTimeMillis()));
        }
        // Query only the user-selected contact's subdirectory under the picker's grant.
        try(Cursor c=activity.getContentResolver().query(Uri.withAppendedPath(uri,ContactsContract.Contacts.Entity.CONTENT_DIRECTORY),null,null,null,null)){
            if(c!=null)while(c.moveToNext()){
                String mime=field(c,ContactsContract.Data.MIMETYPE),value=field(c,ContactsContract.Data.DATA1),label=field(c,ContactsContract.Data.DATA3);
                int type;try{type=Integer.parseInt(field(c,ContactsContract.Data.DATA2));}catch(Exception ignored){type=0;}
                if(mime.equals(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)){out.put("name",field(c,ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME));out.put("surname",field(c,ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME));}
                else if(mime.equals(ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE))out.put("alias",value);
                else if(mime.equals(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE))phones.put(new JSONObject().put("label",ContactsContract.CommonDataKinds.Phone.getTypeLabel(activity.getResources(),type,label)).put("value",value));
                else if(mime.equals(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE))emails.put(new JSONObject().put("label",ContactsContract.CommonDataKinds.Email.getTypeLabel(activity.getResources(),type,label)).put("value",value));
                else if(mime.equals(ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE))addresses.put(new JSONObject().put("label",ContactsContract.CommonDataKinds.StructuredPostal.getTypeLabel(activity.getResources(),type,label)).put("value",value));
                else if(mime.equals(ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)){out.put("organization",value);out.put("role",field(c,ContactsContract.CommonDataKinds.Organization.TITLE));}
                else if(mime.equals(ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)&&!value.isEmpty())notes.put(new JSONObject().put("id",java.util.UUID.randomUUID().toString()).put("date",System.currentTimeMillis()).put("text",value));
            }
        }catch(RuntimeException unavailable){out.put("importNotice","Android shared only basic details. You can fill in the remaining fields manually.");}
        out.put("phones",phones).put("emails",emails).put("addresses",addresses).put("notes",notes);
        try(InputStream in=ContactsContract.Contacts.openContactPhotoInputStream(activity.getContentResolver(),uri,true)){if(in!=null)out.put("photo",thumbnail(readBytes(in,20000000)));}catch(Exception ignored){}
        return out;
    }
}
