package com.maxassistant.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

/** Device-private application state. Never stores provider keys or notification PendingIntents. */
public final class AppStore extends SQLiteOpenHelper {
    private final SharedPreferences preferences;
    public AppStore(Context context) {
        super(context.getApplicationContext(), "max-local.db", null, 2);
        preferences = context.getApplicationContext().getSharedPreferences("max-settings", Context.MODE_PRIVATE);
        setWriteAheadLoggingEnabled(true);
    }
    public SharedPreferences prefs() { return preferences; }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY AUTOINCREMENT,role TEXT NOT NULL,content TEXT NOT NULL,created_at INTEGER NOT NULL,artifact_id TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE memories(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,content TEXT NOT NULL,created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE tasks(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,prompt TEXT NOT NULL,scheduled_at INTEGER NOT NULL,status TEXT NOT NULL DEFAULT 'pending',result TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE INDEX tasks_due ON tasks(status,scheduled_at)");
        db.execSQL("CREATE TABLE inbox(id INTEGER PRIMARY KEY AUTOINCREMENT,package_name TEXT NOT NULL,title TEXT NOT NULL,text TEXT NOT NULL,notification_key TEXT NOT NULL DEFAULT '',received_at INTEGER NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {
        if(oldVersion < 2) db.execSQL("ALTER TABLE messages ADD COLUMN artifact_id TEXT NOT NULL DEFAULT ''");
    }
    private static String clean(String value,int max) {
        if(value == null) return "";
        return value.length() > max ? value.substring(0,max) : value;
    }
    public long addMessage(String role,String content) {
        return addMessage(role,content,"");
    }
    public long addMessage(String role,String content,String artifactId) {
        if(!"user".equals(role) && !"assistant".equals(role)) throw new IllegalArgumentException("Invalid role");
        String artifact=artifactId==null?"":artifactId;
        if(!artifact.isEmpty()&&!artifact.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))throw new IllegalArgumentException("Invalid artifact id");
        ContentValues v = new ContentValues();v.put("role",role);v.put("content",clean(content,30000));v.put("created_at",System.currentTimeMillis());v.put("artifact_id",artifact);
        SQLiteDatabase db=getWritableDatabase();long id=db.insertOrThrow("messages",null,v);
        db.execSQL("DELETE FROM messages WHERE id NOT IN (SELECT id FROM messages ORDER BY id DESC LIMIT 500)");return id;
    }
    public List<Message> getMessages(int limit) {
        List<Message> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,role,content,created_at,artifact_id FROM (SELECT * FROM messages ORDER BY id DESC LIMIT ?) ORDER BY id ASC",new String[]{String.valueOf(Math.max(1,Math.min(limit,500)))})) {
            while(c.moveToNext())out.add(new Message(c.getLong(0),c.getString(1),c.getString(2),c.getLong(3),c.getString(4)));
        }return out;
    }
    public void clearMessages(){getWritableDatabase().delete("messages",null,null);}
    public long addMemory(String title,String content) {
        if(content==null || content.trim().isEmpty())throw new IllegalArgumentException("Memory is empty");
        ContentValues v=new ContentValues();v.put("title",clean(title,200));v.put("content",clean(content,10000));v.put("created_at",System.currentTimeMillis());
        return getWritableDatabase().insertOrThrow("memories",null,v);
    }
    public List<Memory> getMemories(){
        List<Memory> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,title,content,created_at FROM memories ORDER BY id DESC LIMIT 200",null)){
            while(c.moveToNext())out.add(new Memory(c.getLong(0),c.getString(1),c.getString(2),c.getLong(3)));
        }return out;
    }
    public void deleteMemory(long id){getWritableDatabase().delete("memories","id=?",new String[]{String.valueOf(id)});}
    public long addTask(String title,String prompt,long scheduledAt){
        if(prompt==null || prompt.trim().isEmpty())throw new IllegalArgumentException("Task is empty");
        ContentValues v=new ContentValues();v.put("title",clean(title,200));v.put("prompt",clean(prompt,12000));v.put("scheduled_at",scheduledAt);v.put("status","pending");v.put("result","");
        return getWritableDatabase().insertOrThrow("tasks",null,v);
    }
    private static Task task(Cursor c){return new Task(c.getLong(0),c.getString(1),c.getString(2),c.getLong(3),c.getString(4),c.getString(5));}
    public List<Task> getTasks(){
        List<Task> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,title,prompt,scheduled_at,status,result FROM tasks ORDER BY scheduled_at DESC LIMIT 200",null)){while(c.moveToNext())out.add(task(c));}return out;
    }
    public Task getTask(long id){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,title,prompt,scheduled_at,status,result FROM tasks WHERE id=?",new String[]{String.valueOf(id)})){return c.moveToFirst()?task(c):null;}
    }
    public List<Task> getDueTasks(long now){
        List<Task> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,title,prompt,scheduled_at,status,result FROM tasks WHERE status IN ('pending','waiting_unlock') AND scheduled_at<=? ORDER BY scheduled_at ASC LIMIT 10",new String[]{String.valueOf(now)})){while(c.moveToNext())out.add(task(c));}return out;
    }
    public void setTaskStatus(long id,String status,String result){
        if(!java.util.Arrays.asList("pending","running","completed","failed","waiting_unlock","cancelled").contains(status))throw new IllegalArgumentException("Invalid task status");
        ContentValues v=new ContentValues();v.put("status",status);v.put("result",clean(result,30000));getWritableDatabase().update("tasks",v,"id=?",new String[]{String.valueOf(id)});
    }
    /** Only call once when the execution service starts, before its runner accepts jobs. */
    public void recoverRunningTasks(){ContentValues v=new ContentValues();v.put("status","pending");getWritableDatabase().update("tasks",v,"status='running'",null);}
    public void deleteTask(long id){getWritableDatabase().delete("tasks","id=?",new String[]{String.valueOf(id)});}
    public long addInbox(String packageName,String title,String text){return addInbox(packageName,title,text,"");}
    public long addInbox(String packageName,String title,String text,String notificationKey){
        ContentValues v=new ContentValues();v.put("package_name",clean(packageName,200));v.put("title",clean(title,500));v.put("text",clean(text,8000));v.put("notification_key",clean(notificationKey,1000));v.put("received_at",System.currentTimeMillis());
        SQLiteDatabase db=getWritableDatabase();long id=db.insertOrThrow("inbox",null,v);
        db.execSQL("DELETE FROM inbox WHERE id NOT IN (SELECT id FROM inbox ORDER BY id DESC LIMIT 1000)");return id;
    }
    public List<InboxItem> getInbox(int limit){
        List<InboxItem> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,package_name,title,text,notification_key,received_at FROM inbox ORDER BY id DESC LIMIT ?",new String[]{String.valueOf(Math.max(1,Math.min(limit,1000)))})){
            while(c.moveToNext())out.add(new InboxItem(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getLong(5)));
        }return out;
    }
    public void clearInbox(){getWritableDatabase().delete("inbox",null,null);}
    public void clearAllData(){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{for(String table:new String[]{"messages","memories","tasks","inbox"})db.delete(table,null,null);db.setTransactionSuccessful();}finally{db.endTransaction();}}
    public static final class Message{public final long id,createdAt;public final String role,content,artifactId;public Message(long id,String role,String content,long createdAt){this(id,role,content,createdAt,"");}public Message(long id,String role,String content,long createdAt,String artifactId){this.id=id;this.role=role;this.content=content;this.createdAt=createdAt;this.artifactId=artifactId==null?"":artifactId;}}
    public static final class Memory{public final long id,createdAt;public final String title,content;public Memory(long id,String title,String content,long createdAt){this.id=id;this.title=title;this.content=content;this.createdAt=createdAt;}}
    public static final class Task{public final long id,scheduledAt;public final String title,prompt,status,result;public Task(long id,String title,String prompt,long scheduledAt,String status,String result){this.id=id;this.title=title;this.prompt=prompt;this.scheduledAt=scheduledAt;this.status=status;this.result=result;}}
    public static final class InboxItem{public final long id,receivedAt;public final String packageName,title,text,notificationKey;public InboxItem(long id,String packageName,String title,String text,String notificationKey,long receivedAt){this.id=id;this.packageName=packageName;this.title=title;this.text=text;this.notificationKey=notificationKey;this.receivedAt=receivedAt;}}
}
