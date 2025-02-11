/*
Simple DirectMedia Layer
Java source code (C) 2009-2014 Sergii Pylypenko

This software is provided 'as-is', without any express or implied
warranty.  In no event will the authors be held liable for any damages
arising from the use of this software.

Permission is granted to anyone to use this software for any purpose,
including commercial applications, and to alter it and redistribute it
freely, subject to the following restrictions:

1. The origin of this software must not be misrepresented; you must not
   claim that you wrote the original software. If you use this software
   in a product, an acknowledgment in the product documentation would be
   appreciated but is not required. 
2. Altered source versions must be plainly marked as such, and must not be
   misrepresented as being the original software.
3. This notice may not be removed or altered from any source distribution.
*/

package net.sourceforge.clonekeenplus;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.os.Environment;
import android.view.View;

import android.widget.TextView;
import java.net.URLConnection;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.*;
import java.security.SecureRandom;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import java.util.zip.*;
import java.io.*;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import android.content.Context;
import android.content.res.Resources;
import java.util.Arrays;
import android.text.SpannedString;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;

import android.Manifest;
import android.content.pm.PackageManager;

import android.os.storage.StorageManager;
import android.os.storage.OnObbStateChangeListener;
import android.content.res.AssetManager;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;

class CountingInputStream extends BufferedInputStream
{

	private long bytesReadMark = 0;
	private long bytesRead = 0;

	public CountingInputStream(InputStream in, int size) {

		super(in, size);
	}

	public CountingInputStream(InputStream in) {

		super(in);
	}

	public long getBytesRead() {

		return bytesRead;
	}

	public synchronized int read() throws IOException {

		int read = super.read();
		if (read >= 0) {
			bytesRead++;
		}
		return read;
	}

	public synchronized int read(byte[] b, int off, int len) throws IOException {

		int read = super.read(b, off, len);
		if (read >= 0) {
			bytesRead += read;
		}
		return read;
	}

	public synchronized long skip(long n) throws IOException {

		long skipped = super.skip(n);
		if (skipped >= 0) {
			bytesRead += skipped;
		}
		return skipped;
	}

	public synchronized void mark(int readlimit) {

		super.mark(readlimit);
		bytesReadMark = bytesRead;
	}

	public synchronized void reset() throws IOException {

		super.reset();
		bytesRead = bytesReadMark;
	}
}


class DataDownloader extends Thread
{

	public static final String DOWNLOAD_FLAG_FILENAME = "libsdl-DownloadFinished-";

	class StatusWriter
	{
		private TextView Status;
		private MainActivity Parent;
		private SpannedString oldText = new SpannedString("");

		public StatusWriter( TextView _Status, MainActivity _Parent )
		{
			Status = _Status;
			Parent = _Parent;
		}
		public void setParent( TextView _Status, MainActivity _Parent )
		{
			synchronized(DataDownloader.this) {
				Status = _Status;
				Parent = _Parent;
				setText( oldText.toString() );
			}
		}
		
		public void setText(final String str)
		{
			class Callback implements Runnable
			{
				public TextView Status;
				public SpannedString text;
				public void run()
				{
					Status.setText(text);
				}
			}
			synchronized(DataDownloader.this) {
				Callback cb = new Callback();
				oldText = new SpannedString(str);
				cb.text = new SpannedString(str);
				cb.Status = Status;
				if( Parent != null && Status != null )
					Parent.runOnUiThread(cb);
			}
		}
		
	}
	public DataDownloader( MainActivity _Parent, TextView _Status )
	{
		Parent = _Parent;
		Status = new StatusWriter( _Status, _Parent );
		//Status.setText( "Connecting to " + Globals.DataDownloadUrl );
		outFilesDir = Globals.DataDir;
		DownloadComplete = false;
		this.start();
	}
	
	public void setStatusField(TextView _Status)
	{
		synchronized(this) {
			Status.setParent( _Status, Parent );
		}
	}

	@Override
	public void run()
	{
		Parent.getVideoLayout().setOnKeyListener(new BackKeyListener(Parent));

		String [] downloadFiles = Globals.DataDownloadUrl;
		int total = 0;
		int count = 0;
		for( int i = 0; i < downloadFiles.length; i++ )
		{
			if( downloadFiles[i].length() > 0 &&
				( Globals.OptionalDataDownload.length > i && Globals.OptionalDataDownload[i] ) ||
				( Globals.OptionalDataDownload.length <= i && downloadFiles[i].indexOf("!") == 0 ) )
				total += 1;
		}
		for( int i = 0; i < downloadFiles.length; i++ )
		{
			if( downloadFiles[i].length() > 0 &&
				( Globals.OptionalDataDownload.length > i && Globals.OptionalDataDownload[i] ) ||
				( Globals.OptionalDataDownload.length <= i && downloadFiles[i].indexOf("!") == 0 ) )
			{
				if( ! DownloadDataFile(downloadFiles[i].replace("<ARCH>", android.os.Build.CPU_ABI), DOWNLOAD_FLAG_FILENAME + String.valueOf(i) + ".flag", count+1, total, i) )
				{
					if ( ! downloadFiles[i].contains("<ARCH>") || (
							downloadFiles[i].contains("<ARCH>") &&
							! DownloadDataFile(downloadFiles[i].replace("<ARCH>", android.os.Build.CPU_ABI2), DOWNLOAD_FLAG_FILENAME + String.valueOf(i) + ".flag", count+1, total, i) ) )
					{
						DownloadFailed = true;
						if (!Parent.getFilesDir().getAbsolutePath().equals(Globals.DataDir))
						{
							Globals.DataDir = Parent.getFilesDir().getAbsolutePath();
							Globals.DownloadToSdcard = false;
							Log.i("SDL", "Switching download destination directory to internal storage and restarting the app: " + Globals.DataDir);
							Settings.Save(Parent);
							Intent intent = new Intent(Parent, RestartMainActivity.class);
							Parent.startActivity(intent);
							System.exit(0);
						}
						return;
					}
				}
				count += 1;
			}
		}
		DownloadComplete = true;
		Parent.getVideoLayout().setOnKeyListener(null);
		initParent();
	}

	public boolean DownloadDataFile(final String DataDownloadUrl, final String DownloadFlagFileName, int downloadCount, int downloadTotal, int downloadIndex)
	{
		DownloadCanBeResumed = false;
		Resources res = Parent.getResources();

		String [] downloadUrls = DataDownloadUrl.split("[|]");
		if( downloadUrls.length < 2 )
		{
			Log.i("SDL", "Error: download string invalid: '" + DataDownloadUrl + "', your AndroidAppSettigns.cfg is broken");
			Status.setText( res.getString(R.string.error_dl_from, DataDownloadUrl) );
			return false;
		}

		boolean forceOverwrite = false;
		String path = getOutFilePath(DownloadFlagFileName);
		InputStream checkFile = null;
		try {
			checkFile = new FileInputStream( path );
		} catch( FileNotFoundException e ) {
		} catch( SecurityException e ) { };
		if( checkFile != null )
		{
			try {
				byte b[] = new byte[ Globals.DataDownloadUrl[downloadIndex].getBytes("UTF-8").length + 1 ];
				int readed = checkFile.read(b);
				String compare = "";
				if( readed > 0 )
					compare = new String( b, 0, readed, "UTF-8" );
				boolean matched = false;
				//Log.i("SDL", "Read URL: '" + compare + "'");
				for( int i = 1; i < downloadUrls.length; i++ )
				{
					//Log.i("SDL", "Comparing: '" + downloadUrls[i] + "'");
					if( compare.compareTo(downloadUrls[i]) == 0 )
						matched = true;
				}
				//Log.i("SDL", "Matched: " + String.valueOf(matched));
				if( ! matched )
					throw new IOException();
				Status.setText( res.getString(R.string.download_unneeded) );
				return true;
			} catch ( IOException e ) {
				forceOverwrite = true;
				new File(path).delete();
			}
		}
		checkFile = null;
		
		// Create output directory (not necessary for phone storage)
		Log.i("SDL", "Downloading data to: '" + outFilesDir + "'");
		try {
			File outDir = new File( outFilesDir );
			if( !outDir.exists() )
				outDir.mkdirs();
			OutputStream out = new FileOutputStream( getOutFilePath(".nomedia") );
			out.flush();
			out.close();
		}
		catch( SecurityException e ) {}
		catch( FileNotFoundException e ) {}
		catch( IOException e ) {};

		HttpURLConnection request = null;
		long totalLen = 0;
		long partialDownloadLen = 0;
		CountingInputStream stream;
		boolean DoNotUnzip = false;
		boolean FileInAssets = false;
		boolean FileInExpansion = false;
		boolean MountObb = false;
		final boolean[] ObbMounted = new boolean[] { false };
		final boolean[] ObbMountedError = new boolean[] { false };
		String url = "";

		int downloadUrlIndex = 1;
		while( downloadUrlIndex < downloadUrls.length )
		{
			Log.i("SDL", "Processing download " + downloadUrls[downloadUrlIndex]);

			DoNotUnzip = false;
			FileInAssets = false;
			FileInExpansion = false;
			partialDownloadLen = 0;
			totalLen = 0;

			url = new String(downloadUrls[downloadUrlIndex]);
			if(url.indexOf(":") == 0)
			{
				path = getOutFilePath(url.substring( 1, url.indexOf(":", 1) ));
				url = url.substring( url.indexOf(":", 1) + 1 );
				DoNotUnzip = true;
				DownloadCanBeResumed = true;
				File partialDownload = new File( path );
				if( partialDownload.exists() && !partialDownload.isDirectory() && !forceOverwrite )
					partialDownloadLen = partialDownload.length();
			}
			Status.setText( downloadCount + "/" + downloadTotal + ": " + res.getString(R.string.connecting_to, url) );
			if( url.equals("assetpack") )
			{
				Log.i("SDL", "Checking for asset pack");
				/*
				try
				{
					AssetPackManager assetPackManager = AssetPackManagerFactory.getInstance(Parent.getApplicationContext());
					Log.i("SDL", "Parent.getApplicationContext(): " + Parent.getApplicationContext() + " assetPackManager " + assetPackManager);
					if( assetPackManager != null )
					{
						Log.i("SDL", "assetPackManager.getPackLocation(): " + assetPackManager.getPackLocation("assetpack"));
						if( assetPackManager.getPackLocation("assetpack") != null )
						{
							String assetPackPath = assetPackManager.getPackLocation("assetpack").assetsPath();
							Parent.assetPackPath = assetPackPath;
							if( assetPackPath != null )
							{
								Log.i("SDL", "Asset pack is installed at: " + assetPackPath);
								return true;
							}
						}
					}
				}
				catch( Exception e )
				{
					Log.i("SDL", "Asset pack exception: " + e);
				}
				*/
				try
				{
					if( android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP )
					{
						ApplicationInfo info = Parent.getPackageManager().getApplicationInfo(Parent.getPackageName(), 0);
						if( info.splitSourceDirs != null )
						{
							for( String apk: info.splitSourceDirs )
							{
								Log.i("SDL", "Package apk: " + apk);
								if( apk.endsWith("assetpack.apk") )
								{
									Parent.assetPackPath = apk;
								}
							}
						}
					}
				}
				catch( Exception e )
				{
					Log.i("SDL", "Asset pack exception: " + e);
				}
				if( Parent.assetPackPath != null )
				{
					Log.i("SDL", "Found asset pack: " + Parent.assetPackPath);
					return true;
				}
				Log.i("SDL", "Asset pack is not installed");
				downloadUrlIndex++;
				continue;
			}
			else if( url.indexOf("obb:") == 0 || url.indexOf("mnt:") == 0 ) // APK expansion file provided by Google Play
			{
				boolean tmpMountObb = ( url.indexOf("mnt:") == 0 );
				url = getObbFilePath(url);
				InputStream stream1 = null;

				try {
					stream1 = new FileInputStream(url);
					stream1.read();
					stream1.close();
					Log.i("SDL", "Fetching file from expansion: " + url);
					FileInExpansion = true;
					MountObb = tmpMountObb;
					break;
				} catch( IOException ee ) {
					Log.i("SDL", "Failed to open file, requesting storage read permission: " + url);

					if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M)
					{
						int permissionCheck = Parent.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
						if (permissionCheck != PackageManager.PERMISSION_GRANTED && !Parent.readExternalStoragePermissionDialogAnswered)
						{
							Parent.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
							while( !Parent.readExternalStoragePermissionDialogAnswered )
							{
								try{ Thread.sleep(300); } catch (InterruptedException e) {}
							}
						}
					}
				} catch( Exception eee ) {
					Log.i("SDL", "Failed to open file: " + url);
					downloadUrlIndex++;
					continue;
				}

				try {
					stream1 = new FileInputStream(url);
					stream1.read();
					stream1.close();
					Log.i("SDL", "Fetching file from expansion: " + url);
					FileInExpansion = true;
					MountObb = tmpMountObb;
					break;
				} catch( Exception eee ) {
					Log.i("SDL", "Failed to open file: " + url);
					downloadUrlIndex++;
					continue;
				}
			}
			else if( url.indexOf("http://") == -1 && url.indexOf("https://") == -1 ) // File inside assets
			{
				InputStream stream1 = null;
				try {
					stream1 = Parent.getAssets().open(url);
					stream1.close();
				} catch( Exception e ) {
					try {
						stream1 = Parent.getAssets().open(url + "000");
						stream1.close();
					} catch( Exception ee ) {
						Log.i("SDL", "Failed to open file in assets: " + url);
						downloadUrlIndex++;
						continue;
					}
				}
				FileInAssets = true;
				Log.i("SDL", "Fetching file from assets: " + url);
				break;
			}
			else
			{
				Log.i("SDL", "Connecting to: " + url);
				try {
					request = (HttpURLConnection)(new URL(url).openConnection());  //new HttpGet(url);
					while (true)
					{
						request.setRequestProperty("Accept", "*/*");
						request.setFollowRedirects(false);
						if( partialDownloadLen > 0 )
						{
							request.setRequestProperty("Range", "bytes=" + partialDownloadLen + "-");
							Log.i("SDL", "Trying to resume download at pos " + partialDownloadLen);
						}
						request.connect();
						Log.i("SDL", "Got HTTP response " + request.getResponseCode() + " " + request.getResponseMessage() + " type " + request.getContentType() + " length " + request.getContentLength());
						if (request.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP ||
							request.getResponseCode() == HttpURLConnection.HTTP_MOVED_PERM ||
							request.getResponseCode() == HttpURLConnection.HTTP_SEE_OTHER ||
							request.getResponseCode() == 307 || request.getResponseCode() == 308)
						{
							String oldUrl = request.getURL().toString();
							String cookie = request.getHeaderField("Set-Cookie");
							request = (HttpURLConnection)(new URL(request.getHeaderField("Location")).openConnection());
							Log.i("SDL", "Following HTTP redirect to " + request.getURL().toString());
							//request.addRequestProperty("Referer", oldUrl);
							//if (cookie != null)
							//	request.addRequestProperty("Cookie", cookie);
							continue;
						}
						request.getInputStream();
						break;
					}
				} catch ( Exception e ) {
					Log.i("SDL", "Failed to connect to " + url + " with error " + e.toString());
					request = null;
					downloadUrlIndex++;
					continue;
				}
				break;
			}
		}
		
		if( MountObb )
		{
			Log.i("SDL", "Mounting OBB file: " + url);
			StorageManager sm = (StorageManager) Parent.getSystemService(Context.STORAGE_SERVICE);
			if( !sm.mountObb(url, null, new OnObbStateChangeListener()
					{
						public void onObbStateChange(String path, int state)
						{
							if (state == OnObbStateChangeListener.MOUNTED ||
								state == OnObbStateChangeListener.ERROR_ALREADY_MOUNTED)
							{
								ObbMounted[0] = true;
							}
							else
							{
								ObbMountedError[0] = true;
							}
						}
					}) )
			{
				Log.i("SDL", "Cannot mount OBB file '" + url + "'");
				Status.setText( res.getString(R.string.error_dl_from, url) );
				return false;
			}
			while( !ObbMounted[0] )
			{
				try{ Thread.sleep(300); } catch (InterruptedException e) {}
				if( ObbMountedError[0] )
				{
					Log.i("SDL", "Cannot mount OBB file '" + url + "'");
					Status.setText( res.getString(R.string.error_dl_from, url) );
					return false;
				}
			}
			Parent.ObbMountPath = sm.getMountedObbPath(url);
			if( Parent.ObbMountPath == null )
			{
				Log.i("SDL", "Cannot mount OBB file '" + url + "'");
				Status.setText( res.getString(R.string.error_dl_from, url) );
				return false;
			}
			Log.i("SDL", "Mounted OBB file '" + url + "' to path " + Parent.ObbMountPath);
			return true;
		}

		if( FileInExpansion )
		{
			Log.i("SDL", "Count file size: '" + url);
			try {
				totalLen = new File(url).length();
				stream = new CountingInputStream(new FileInputStream(url), 8192);
				Log.i("SDL", "Count file size: '" + url + " = " + totalLen);
			} catch( IOException e ) {
				Log.i("SDL", "Unpacking from filesystem '" + url + "' - error: " + e.toString());
				Status.setText( res.getString(R.string.error_dl_from, url) );
				return false;
			}
		}
		else if( FileInAssets )
		{
			int multipartCounter = 0;
			InputStream multipart = null;
			while( true )
			{
				try {
					// Make string ".zip000", ".zip001" etc for multipart archives
					String url1 = url + String.format("%03d", multipartCounter);
					CountingInputStream stream1 = new CountingInputStream(Parent.getAssets().open(url1), 8192);
					while( stream1.skip(65536) > 0 ) { };
					totalLen += stream1.getBytesRead();
					stream1.close();
					InputStream s = Parent.getAssets().open(url1);
					if( multipart == null )
						multipart = s;
					else
						multipart = new SequenceInputStream(multipart, s);
					Log.i("SDL", "Multipart archive found: " + url1);
				} catch( IOException e ) {
					break;
				}
				multipartCounter += 1;
			}
			if( multipart != null )
				stream = new CountingInputStream(multipart, 8192);
			else
			{
				try {
					stream = new CountingInputStream(Parent.getAssets().open(url), 8192);
					while( stream.skip(65536) > 0 ) { };
					totalLen = stream.getBytesRead();
					stream.close();
					stream = new CountingInputStream(Parent.getAssets().open(url), 8192);
				} catch( IOException e ) {
					Log.i("SDL", "Unpacking from assets '" + url + "' - error: " + e.toString());
					Status.setText( res.getString(R.string.error_dl_from, url) );
					return false;
				}
			}
		}
		else
		{
			if( request == null )
			{
				Log.i("SDL", "Error connecting to " + url);
				Status.setText( res.getString(R.string.failed_connecting_to, url) );
				return false;
			}

			Status.setText( downloadCount + "/" + downloadTotal + ": " + res.getString(R.string.dl_from, url) );
			totalLen = request.getContentLength();
			try {
				stream = new CountingInputStream(request.getInputStream(), 8192);
			} catch( java.io.IOException e ) {
				Status.setText( res.getString(R.string.error_dl_from, url) );
				return false;
			}
		}

		if( !copyUnpackFileStream(stream, path, url, DoNotUnzip, FileInAssets, FileInExpansion, totalLen, partialDownloadLen, request, downloadCount, downloadTotal) )
			return false;

		OutputStream out = null;
		path = getOutFilePath(DownloadFlagFileName);
		try {
			out = new FileOutputStream( path );
			out.write(downloadUrls[downloadUrlIndex].getBytes("UTF-8"));
			out.flush();
			out.close();
		} catch( java.io.IOException e ) {
			Status.setText( res.getString(R.string.error_write, path) + ": " + e.getMessage() );
			return false;
		};
		Status.setText( downloadCount + "/" + downloadTotal + ": " + res.getString(R.string.dl_finished) );

		try {
			stream.close();
		} catch( java.io.IOException e ) {
		};

		return true;
	};

	// Moved part of code to a separate method, because Android imposes a stupid limit on Java method size
	private boolean copyUnpackFileStream(	CountingInputStream stream, String path, String url,
											boolean DoNotUnzip, boolean FileInAssets, boolean FileInExpansion,
											long totalLen, long partialDownloadLen, URLConnection response,
											int downloadCount, int downloadTotal)
	{
		long updateStatusTime = 0;
		byte[] buf = new byte[16384];
		Resources res = Parent.getResources();

		if(DoNotUnzip)
		{
			Log.i("SDL", "Saving file '" + path + "'");
			OutputStream out = null;
			try {
				try {
					File outDir = new File( path.substring(0, path.lastIndexOf("/") ));
					if( !(outDir.exists() && outDir.isDirectory()) )
						outDir.mkdirs();
				} catch( SecurityException e ) { };

				if( partialDownloadLen > 0 )
				{
					try {
						String range = response.getHeaderField("Content-Range");
						if( range != null && range.indexOf("bytes") == 0 )
						{
							//Log.i("SDL", "Resuming download of file '" + path + "': Content-Range: " + range[0].getValue());
							String[] skippedBytes = range.split("/")[0].split("-")[0].split(" ");
							if( skippedBytes.length >= 2 && Long.parseLong(skippedBytes[1]) == partialDownloadLen )
							{
								out = new FileOutputStream( path, true );
								Log.i("SDL", "Resuming download of file '" + path + "' at pos " + partialDownloadLen);
							}
						}
						else
							Log.i("SDL", "Server does not support partial downloads. ");
					} catch (Exception e) { }
				}
				if( out == null )
				{
					out = new FileOutputStream( path );
					partialDownloadLen = 0;
				}
			} catch( FileNotFoundException e ) {
				Log.i("SDL", "Saving file '" + path + "' - error creating output file: " + e.toString());
			} catch( SecurityException e ) {
				Log.i("SDL", "Saving file '" + path + "' - error creating output file: " + e.toString());
			};
			if( out == null )
			{
				Status.setText( res.getString(R.string.error_write, path) );
				Log.i("SDL", "Saving file '" + path + "' - error creating output file");
				return false;
			}

			try {
				int len = stream.read(buf);
				while (len >= 0)
				{
					if(len > 0)
						out.write(buf, 0, len);
					len = stream.read(buf);

					float percent = 0.0f;
					if( totalLen > 0 )
						percent = (stream.getBytesRead() + partialDownloadLen) * 100.0f / (totalLen + partialDownloadLen);
					if( System.currentTimeMillis() > updateStatusTime + 1000 )
					{
						updateStatusTime = System.currentTimeMillis();
						Status.setText( downloadCount + "/" + downloadTotal + ": " + res.getString(R.string.dl_progress, percent, path) );
					}
				}
				out.flush();
				out.close();
				out = null;
			} catch( java.io.IOException e ) {
				Status.setText( res.getString(R.string.error_write, path) + ": " + e.getMessage() );
				Log.i("SDL", "Saving file '" + path + "' - error writing: " + e.toString());
				return false;
			}
			Log.i("SDL", "Saving file '" + path + "' done");
		}
		else
		{
			Log.i("SDL", "Reading from zip file '" + url + "'");
			ZipInputStream zip;
			if (url.endsWith(".zip.xz") || url.endsWith(".zip.xz/download"))
				try
				{
					if (!Arrays.asList(Globals.AppLibraries).contains("lzma"))
						throw new IOException("LZMA support not compiled in - add lzma to CompiledLibraries inside AndroidAppSettings.cfg");
					zip = new ZipInputStream(new XZInputStream(stream));
				}
				catch (Exception eeeee)
				{
					Log.i("SDL", "Opening file '" + url + "' failed - cannot open XZ input stream: " + eeeee.toString());
					return false;
				}
			else
				zip = new ZipInputStream(stream);

			String extpath = getOutFilePath("");
			
			while(true)
			{
				ZipEntry entry = null;
				try {
					entry = zip.getNextEntry();
					if( entry != null )
						Log.i("SDL", "Reading from zip file '" + url + "' entry '" + entry.getName() + "'");
				} catch( java.io.IOException e ) {
					Status.setText( res.getString(R.string.error_dl_from, url) );
					Log.i("SDL", "Error reading from zip file '" + url + "': " + e.toString());
					return false;
				}
				if( entry == null )
				{
					Log.i("SDL", "Reading from zip file '" + url + "' finished");
					break;
				}
				if( entry.isDirectory() )
				{
					Log.i("SDL", "Creating dir '" + getOutFilePath(entry.getName()) + "'");
					try {
						File outDir = new File( getOutFilePath(entry.getName()) );
						if( !(outDir.exists() && outDir.isDirectory()) )
							outDir.mkdirs();
					} catch( SecurityException e ) { };
					continue;
				}

				OutputStream out = null;
				path = getOutFilePath(entry.getName());
				float percent = 0.0f;

				Log.i("SDL", "Saving file '" + path + "'");

				try {
					File outDir = new File( path.substring(0, path.lastIndexOf("/") ));
					if( !(outDir.exists() && outDir.isDirectory()) )
						outDir.mkdirs();
				} catch( SecurityException e ) { };
				
				try {
					CheckedInputStream check = new CheckedInputStream( new FileInputStream(path), new CRC32() );
					while( check.read(buf, 0, buf.length) >= 0 ) {};
					check.close();
					if( check.getChecksum().getValue() != entry.getCrc() )
					{
						File ff = new File(path);
						ff.delete();
						throw new Exception();
					}
					Log.i("SDL", "File '" + path + "' exists and passed CRC check - not overwriting it");
					if( totalLen > 0 )
						percent = stream.getBytesRead() * 100.0f / totalLen;
					if( System.currentTimeMillis() > updateStatusTime + 1000 )
					{
						updateStatusTime = System.currentTimeMillis();
						Status.setText( downloadCount + "/" + downloadTotal + ": " + res.getString(R.string.dl_progress, percent, path) );
					}
					continue;
				} catch( Exception e ) { }

				try {
					out = new FileOutputStream( path );
				} catch( FileNotFoundException e ) {
					Log.i("SDL", "Saving file '" + path + "' - cannot create file: " + e.toString());
				} catch( SecurityException e ) {
					Log.i("SDL", "Saving file '" + path + "' - cannot create file: " + e.toString());
				};
				if( out == null )
				{
					Status.setText( res.getString(R.string.error_write, path) );
					Log.i("SDL", "Saving file '" + path + "' - cannot create file");
					return false;
				}

				if( totalLen > 0 )
					percent = stream.getBytesRead() * 100.0f / totalLen;
				//Unpacking local zip file into external storage
				if( System.currentTimeMillis() > updateStatusTime + 1000 )
				{
					updateStatusTime = System.currentTimeMillis();
					Status.setText( downloadCount + "/" + downloadTotal + ": " + res.getString(R.string.dl_progress, percent, path.replace(extpath, "")) );
				}
				
				try {
					int len = zip.read(buf);
					while (len >= 0)
					{
						if(len > 0)
							out.write(buf, 0, len);
						len = zip.read(buf);

						percent = 0.0f;
						if( totalLen > 0 )
							percent = stream.getBytesRead() * 100.0f / totalLen;
						if( System.currentTimeMillis() > updateStatusTime + 1000 )
						{
							updateStatusTime = System.currentTimeMillis();
							Status.setText( downloadCount + "/" + downloadTotal + ": " + res.getString(R.string.dl_progress, percent, path.replace(extpath, "")) );
						}
					}
					out.flush();
					out.close();
					out = null;
				} catch( java.io.IOException e ) {
					Status.setText( res.getString(R.string.error_write, path) + ": " + e.getMessage() );
					Log.i("SDL", "Saving file '" + path + "' - error writing or downloading: " + e.toString());
					return false;
				}
				
				try {
					long count = 0, ret = 0;
					CheckedInputStream check = new CheckedInputStream( new FileInputStream(path), new CRC32() );
					while( ret >= 0 )
					{
						count += ret;
						ret = check.read(buf, 0, buf.length);
					}
					check.close();


					// NOTE: For some reason this not work properly on older Android versions (4.4 and below). 
					// Setting this to become a warning
					if( check.getChecksum().getValue() != entry.getCrc() || count != entry.getSize() )
					{
						//File ff = new File(path);
						//ff.delete();
						Log.i("SDL", "Saving file '" + path + "' - CRC check failed, ZIP: " +
											String.format("%x", entry.getCrc()) + " actual file: " + String.format("%x", check.getChecksum().getValue()) +
											" file size in ZIP: " + entry.getSize() + " actual size " + count );
						Log.i("SDL", "If you still get problems try to reset the app or delete file at path " + path );
						//throw new Exception();
					}
				} catch( Exception e ) {
					Status.setText( res.getString(R.string.error_write, path) + ": " + e.getMessage() );
					return false;
				}
				Log.i("SDL", "Saving file '" + path + "' done");
			}
		};
		return true;
	}

	private void initParent()
	{
		class Callback implements Runnable
		{
			public MainActivity Parent;
			public void run()
			{
				Parent.initSDL();
			}
		}
		Callback cb = new Callback();
		synchronized(this) {
			cb.Parent = Parent;
			if(Parent != null)
				Parent.runOnUiThread(cb);
		}
	}
	
	private String getOutFilePath(final String filename)
	{
		return outFilesDir + "/" + filename;
	};

	private String getObbFilePath(final String url)
	{
		// "obb:" or "mnt:" - same length
		return Environment.getExternalStorageDirectory().getAbsolutePath() + "/Android/obb/" +
				Parent.getPackageName() + "/" + url.substring("obb:".length()) + "." + Parent.getPackageName() + ".obb";
	}

	public class BackKeyListener implements View.OnKeyListener
	{
		MainActivity p;
		public BackKeyListener(MainActivity _p)
		{
			p = _p;
		}

		@Override
		public boolean onKey(View v, int keyCode, KeyEvent event)
		{
			if( DownloadFailed )
				System.exit(1);

			AlertDialog.Builder builder = new AlertDialog.Builder(p);
			builder.setTitle(p.getResources().getString(R.string.cancel_download));
			builder.setMessage(p.getResources().getString(R.string.cancel_download) + (DownloadCanBeResumed ? " " + p.getResources().getString(R.string.cancel_download_resume) : ""));
			
			builder.setPositiveButton(p.getResources().getString(R.string.yes), new DialogInterface.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int item) 
				{
					System.exit(1);
					dialog.dismiss();
				}
			});
			builder.setNegativeButton(p.getResources().getString(R.string.no), new DialogInterface.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int item) 
				{
					dialog.dismiss();
				}
			});
			builder.setOnCancelListener(new DialogInterface.OnCancelListener()
			{
				public void onCancel(DialogInterface dialog)
				{
				}
			});
			AlertDialog alert = builder.create();
			alert.setOwnerActivity(p);
			alert.show();
			return true;
		}
	}

	public StatusWriter Status;
	public boolean DownloadComplete = false;
	public boolean DownloadFailed = false;
	public boolean DownloadCanBeResumed = false;
	private MainActivity Parent;
	private String outFilesDir = null;
}

