package myjin.pro.ahoora.wifi_.test_;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import org.apache.commons.net.ftp.FTPFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Arrays;

public class FTPClient {
    org.apache.commons.net.ftp.FTPClient ftpClient = new org.apache.commons.net.ftp.FTPClient();

    // for FTP server credentials
    private String hostname;
    private String password;
    private String username;

    //port for connection
    private int port;

    //Local and remote directory on device for files being downloaded.
    private File localPath;
    private String remotePath;

    //Is the client connected?
    private Object connectedThreadLock = new Object();
    private boolean connected = false;
    private boolean syncing = false;


    /**
     * Constructor without port option
     * @param hostname: Server IP Adress
     * @param username: Login Credential
     * @param password: Login Credential
     * @param localPath: Local path for saving to the device
     */
    public FTPClient(String hostname, String username, String password, String localPath){
        this.hostname = hostname;
        this.password = password;
        this.username = username;
        this.localPath = new File(Environment.getExternalStorageDirectory() + localPath);
        this.remotePath = localPath;
        this.port = 21;
    }

    /**
     * Constructor with port option
     * @param hostname: Server IP Adress
     * @param username: Login Credential
     * @param password: Login Credential
     * @param localPath: Local path for saving to the device
     * @param port: Custom port connection
     */
    public FTPClient(String hostname, String username, String password, String localPath, int port){
        this.hostname = hostname;
        this.password = password;
        this.username = username;
        this.localPath = new File(localPath);
        this.port = port;
    }

    /**
     * Tells you whether the FTPClient is connected or not.
     *
     * @return whether the FTPClient is connected or not.
     **/
    public boolean isConnected() {
        synchronized (connectedThreadLock) {
            return connected;
        }
    }
    /**
     * Tries to connect to a server with the parameters supplied with constructor
     *
     * @throws ConnectException
     */
    public void connect() throws ConnectException {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                synchronized (connectedThreadLock) {
                    try {
                        ftpClient.connect(hostname, port);
                        Log.i("FTPClient|INFO", ftpClient.getReplyString());
                        ftpClient.enterLocalPassiveMode();
                        ftpClient.login(username, password);
                        Log.i("FTPClient|INFO", ftpClient.getReplyString());
                        connected = true;
                    } catch (Exception e) {
                        Log.e("FTPClient|connect", e.toString());
                        connected = false;
                    }
                }
            }
        });
        try {
            Thread.sleep(5);  //multi-threaded voodoo. Give the AsyncTask 5 ms to get started and get the lock.
        } catch (InterruptedException e) {
            // do nothing.
        }
        synchronized (connectedThreadLock) {
            if (!connected) {
                throw new ConnectException("FTPClient failed to connect to FTP server");
            }
        }
    }

    /**
     * Gets a directory listing of the current working directory
     * @param requester: Callback for the thread
     * @throws ConnectException
     */
    public void dir(final FTPRequester requester) throws ConnectException{
        synchronized (connectedThreadLock) {
            if (!connected) {
                throw new ConnectException("You cannot get a directory listing if you are not connected!");
            }
        }
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                Log.i("FTPClient", "Starting DIR");
                try {
                    FTPFile[] files = ftpClient.listFiles();  // files
                    Log.i("FTPClient|INFO", ftpClient.getReplyString());
                    requester.dirCallback(files);
                } catch (Exception e) {
                    Log.e("FTPClient|dir", e.toString());
                    //DIR failed, log the error to console.
                }
            }
        });
    }

    /**
     * Downloads a file from the server to the localPath
     * @param filename: Name of the file on the server
     * @param requester: callback for the thread
     * @throws ConnectException
     */
    public void downloadFile(final String filename, final FTPRequester requester)throws ConnectException {
        synchronized (connectedThreadLock){
            if(!connected){
                throw new ConnectException("You cannot download a file if you're not connected!");
            }
        }
        final File file = new File(Environment.getExternalStorageDirectory() + filename);
        final File folder = new File(file.getParent());
        boolean success = true;
        if (!folder.exists()) {
            success = folder.mkdir();
        }
        if (success) {
            // Do something on success
        } else {
            Log.e("FTPClient", "Unable to create directorys they might already exist!");
        }
        final String RemotePath = filename;
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    OutputStream os = new FileOutputStream(file.getAbsolutePath());
                    ftpClient.retrieveFile(RemotePath, os);
                    requester.downloadFileCallback(file.getAbsolutePath(), RemotePath);
                }catch(Exception e){
                    Log.e("FTPClient|downloadFile", e.toString());
                    return;
                }
            }
        });
    }

    /**
     * Uploads a file from the device to the server
     * @param filename: Path of file on device
     * @param requester: Callback for thread
     * @throws ConnectException
     */
    public void uploadFile(final String filename, final FTPRequester requester)throws ConnectException {
        synchronized (connectedThreadLock){
            if(!connected){
                throw new ConnectException("You cannot upload a file if you're not connected!");
            }
        }
        final String RemotePath = filename;
        AsyncTask.execute(new Runnable(){
            @Override
            public void run(){
                try {
                    InputStream is = new FileInputStream(filename);
                    ftpClient.storeFile(RemotePath, is);
                    requester.uploadFileCallback(localPath.getAbsolutePath(), RemotePath);
                }catch(Exception e){
                    Log.e("FTPClient|uploadFile", e.toString());
                    return;
                }
            }
        });
    }

    /**
     * Downloads all missing files on device from the server, and
     * uploads all missing files on server from device.
     * @param requester: Callback for thread
     * @throws ConnectException
     */

    public void syncAllFiles(final FTPRequester requester, final Activity activity)throws ConnectException{
      synchronized (connectedThreadLock){
            if(!connected){
                throw new ConnectException("You cannot sync files if you're not connected!");
            }
        }
        if(syncing){
            Log.e("FTPClient|INFO", "A sync is already in progress!");
            return;
        }
        AsyncTask.execute(new Runnable() {
            @Override
            public void run(){
                Log.i("FTPClient|INFO", "Starting SYNC");
                requester.updateSyncBar("^checking for differences...", 0, activity);
                int currentProgress = 0;
                int maxUpProgress = 0;
                int maxDownProgress = 0;
                ArrayList<String> localNames;
                ArrayList<String> remoteNames;
                ArrayList<String> filesToUpload = new ArrayList<String>();
                ArrayList<String> filesToDownload = new ArrayList<String>();
                int changed = 0;
                int Uploaded = 0;
                int Downloaded = 0;
                int Unchanged = 0;
                localNames = getLocalDir();
                try{
                    remoteNames = getRemoteDir("/", "", 0);
                }catch(Exception e) {
                    Log.e("FTPClient|sync", "Failed to get remote file listing");
                    requester.updateSyncBar("Error while syncing, see debug for more info.", 100, activity);
                    syncing = false;
                    return;
                }
                Log.i("FTPClient|INFO", "Local Path: " + localPath.toString() + "/");
                try {
                    for (String remoteName : remoteNames) {
                        String usableName = localPath.getAbsolutePath() + remoteName;
                        if (!localNames.contains(usableName))
                            filesToDownload.add(remoteName);
                    }
                    for (String localName : localNames) {
                        String usableName = localName.split(localPath.getAbsolutePath())[1];
                        if(localName==localPath.getAbsolutePath()) continue;
                        if (!remoteNames.contains(usableName))
                            filesToUpload.add(localName);
                        else
                            Unchanged += 1;
                    }
                    maxDownProgress = filesToDownload.size();
                    maxUpProgress = filesToUpload.size();
                    for (String fileToDownload : filesToDownload) {
                        String newFile = fileToDownload;
                        downloadSync(fileToDownload);
                        changed += 1;
                        Downloaded += 1;
                        currentProgress += 1;
                        String display = "test";
                        requester.updateSyncBar("Downloading file " + currentProgress + "/" + maxDownProgress + ":\n" + display, (currentProgress*100) / maxDownProgress, activity);
                    }
                    currentProgress = 0;
                    for (String fileToUpload : filesToUpload) {
                        String newFile = fileToUpload;
                        uploadSync(fileToUpload);
                        changed += 1;
                        Uploaded += 1;
                        currentProgress += 1;
                        String display = newFile.split("frc2706")[1];
                        requester.updateSyncBar("Uploading file " + currentProgress + "/" + maxUpProgress + ":\n" + display, (currentProgress*100) / maxUpProgress, activity);
                    }
                    requester.syncCallback(changed);

                }catch(Exception e){
                    Log.e("FTPClient|sync", e.toString());
                    changed = -1;
                }
                String up = String.valueOf(Uploaded);
                String down = String.valueOf(Downloaded);
                String unchanged = String.valueOf(Unchanged);
                if(changed<1)
                    if(changed==0)
                        requester.updateSyncBar("Your device had the latest files!", 100, activity);
                    else
                        requester.updateSyncBar("Error while syncing, see debug for more info.", 100, activity);
                else
                    requester.updateSyncBar("Done syncing! Unchanged Files: "+Unchanged+"\n"+down+" downloaded, "+up+" uploaded.", 100, activity);
                Log.d("FTPClient|INFO", "Sync done!");

            }
        });
    }
    private void uploadSync(String filename){
        String RemotePath = filename.split(localPath.getAbsolutePath())[1];
        Log.i("FTPClient|uploadSync", "\nUploading: " + filename + "\nTo: " + RemotePath);
        try {
            checkFilepath(filename, true);
            InputStream is = new FileInputStream(filename);
            ftpClient.storeFile(RemotePath, is);
        }catch(Exception e) {
            Log.e("FTPClient|uploadSync", e.toString());
            return;
        }
    }
    private void downloadSync(String RemotePath){
        String filename = localPath.getAbsolutePath() + RemotePath;
        Log.i("FTPClient|downloadSync", "Downloading: " + RemotePath + "\nTo: " + filename);
        try {
            String temp = checkFilepath(filename, false);
            Log.d("FTPClient|CreatedDir?", temp);
            OutputStream os = new FileOutputStream(filename);
            ftpClient.retrieveFile(RemotePath, os);
        }catch(Exception e){
            Log.e("FTPClient|downloadSync", e.toString());
            return;
        }
    }

    public ArrayList<String> getLocalDir(){
        String topLevelPath = localPath.getAbsolutePath();
        ArrayList<String> filenames = new ArrayList<>();
        ArrayList<String> localDirSlaveReturn = new ArrayList<>();
        File topLevel = new File(topLevelPath);
        File[] topDir = topLevel.listFiles();
        for(File file : topDir){
            if(file.isDirectory()){
                filenames.addAll(localDirSlave(file.getAbsolutePath()));
            }else{
                if(!filenames.contains(file.getAbsolutePath()))
                    filenames.add(file.getAbsolutePath());
            }
        }
        return filenames;
    }
    private ArrayList<String> localDirSlave(String currentPath){
        ArrayList<String> filenames = new ArrayList<>();
        File newLevel = new File(currentPath);
        for(File file : newLevel.listFiles()){
            if(file.isDirectory()){
                filenames.addAll(localDirSlave(file.getAbsolutePath()));
            }else{
                if(!filenames.contains(file.getAbsolutePath()))
                    filenames.add(file.getAbsolutePath());
            }
        }
        return filenames;
    }
    public ArrayList<String> getRemoteDir(String parentDir, String currentDir, int level) throws IOException {
        ArrayList<String> filenames = new ArrayList<>();
        String dirToList = parentDir;
        if (!currentDir.equals("")) {
            dirToList += "/" + currentDir;
        }
        ftpClient.changeWorkingDirectory(dirToList);
        FTPFile[] subFiles = ftpClient.listFiles();
        if (subFiles != null && subFiles.length > 0) {
            for (FTPFile aFile : subFiles) {
                String currentFileName = aFile.getName();
                if (currentFileName.equals(".") || currentFileName.equals("..")) {
                    continue;
                }
                for (int i = 0; i < level; i++) {
                    System.out.print("\t");
                }
                if (aFile.isDirectory()) {
                    filenames.addAll(getRemoteDir(dirToList, currentFileName, level + 1));
                } else {
                    String nameToAdd = ftpClient.printWorkingDirectory() + "/" + aFile.getName();
                    if(!filenames.contains(nameToAdd))
                    filenames.add(nameToAdd);
                }
            }
        }
        return filenames;
    }
    private String checkFilepath(String filename, boolean isFTP){
        if(isFTP){
            ArrayList<String> directorys = new ArrayList<>();
            filename = filename.split("frc2706")[1];
            String[] filenames = filename.split("/");
            for(String file : filenames){
                directorys.add(file);
            }
            directorys.remove(directorys.size()-1);
            String parentDir = "";
            for(String dir : directorys){
                parentDir += "/" + dir;
            }
            try{
                return ftpClient.makeDirectory(parentDir) ? "ftpGood" : "ftpFail";
            }catch(Exception e){
                Log.e("FTPClient|checkFilePath", e.toString());
            }
            return "ftpFail";
        }else {
            File file = new File(filename);
            Log.d("FTPClient|checkFilePath", "checking for parent of: " + file.getAbsolutePath());
            File parentDir = file.getParentFile();
            if (!parentDir.exists()) {
                return parentDir.mkdirs() ? "localGood": "localFail";
            } else {
                return "localAlreadyExists";
            }
        }
    }
}
