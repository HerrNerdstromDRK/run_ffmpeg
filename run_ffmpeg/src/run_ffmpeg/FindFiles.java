package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import com.mongodb.client.MongoCollection;

import run_ffmpeg.ffmpeg.FFmpeg_ProbeResult;

/**
 * Multi-threaded class to find files with given extensions in the given folders. Returns
 *  a List of Files with the resulting files found.
 */
public class FindFiles extends run_ffmpegControllerThreadTemplate< FindFilesWorkerThread >
{
	/// File name to which to log activities for this application.
	private static final String logFileName = "log_transcode_and_move_files.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	private static final String stopFileName = "C:\\Temp\\stop_transcode_and_move_files.txt" ;

	/// The folders to search. Each folder will receive its own thread to conduct the search.
	private List< String > foldersToSearch = new ArrayList< String >() ;

	/// The extensions for which to search in the give folders.
	private List< String > extensionsToFind = new ArrayList< String >() ;

	/// The list of Files to return. This will be populated by the worker threads until complete.
	private List< File > filesToReturn = new ArrayList< File >() ;

	/**
	 * Need list of folder names at object creation in order to build the worker threads.
	 * @param folderNames
	 */
	public FindFiles()
	{
		super( logFileName, stopFileName ) ;
		initObject() ;
	}

	/**
	 * Need list of folder names at object creation in order to build the worker threads.
	 */
	public FindFiles( Logger log,
			Common common,
			MoviesAndShowsMongoDB masMDB,
			MongoCollection< FFmpeg_ProbeResult > probeInfoCollection )
	{
		super( log, common, stopFileName, masMDB, probeInfoCollection ) ;
		initObject() ;
	}

	private void initObject()
	{}

	public static void main( String[] args )
	{
		List< String > folderNames = new ArrayList< String >() ;
		
		FindFiles moveFiles = new FindFiles() ;
		folderNames.addAll( Common.getAllMediaFolders() ) ;
		moveFiles.addFoldersToSearch( folderNames ) ;
		moveFiles.addExtensionsToFind( TranscodeCommon.getTranscodeExtensions() ) ;
		
		List< File > foundFiles = moveFiles.getFiles() ;
		moveFiles.log.info( "Found " + foundFiles.size() + " file(s)" ) ;
		
		System.out.println( "Process shut down." ) ;
	}

	public List< File > getFiles()
	{
		Init() ;
		Execute() ;

		// The threads will populate the filesToReturn List with the files that are found.
		return filesToReturn ;
	}

	/**
	 * Initialize this object.
	 */
	@Override
	public void Init()
	{}

	/**
	 * Build the worker threads for this instance. Will create one worker thread for each folder.
	 */
	@Override
	protected List< FindFilesWorkerThread > buildWorkerThreads()
	{
		List< FindFilesWorkerThread > threads = new ArrayList< FindFilesWorkerThread >() ;
		for( String folder : foldersToSearch )
		{
			FindFilesWorkerThread workerThread = new FindFilesWorkerThread( this, log, common, folder, getExtensionsToFind() ) ;
			workerThread.setName( folder ) ;
			threads.add( workerThread ) ;
		}
		return threads ;
	}
	
	public void addExtensionsToFind( final List< String > newExtensions )
	{
		extensionsToFind.addAll( newExtensions ) ;
	}

	public void addExtensionsToFind( final String[] newExtensions )
	{
		addExtensionsToFind( Arrays.asList( newExtensions ) ) ;
	}
	
	public void addFoldersToSearch( final List< String > newFolderNames )
	{
		foldersToSearch.addAll( newFolderNames ) ;
	}

	public void addFoundFiles( final List< File > foundFiles )
	{
		synchronized( filesToReturn )
		{
			filesToReturn.addAll( foundFiles ) ;
		}
	}

	public List<String> getExtensionsToFind()
	{
		return extensionsToFind ;
	}

}
