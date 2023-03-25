package run_ffmpeg;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * The purpose of this class is to move mkv and mp4 files in parallel using one thread
 *  for each type, plus a controller thread.
 * The class uses a static singleton, wherein the only interfaces are through
 *  the methods to add move requests. It is the responsibility of the caller
 *  to check for thread liveness before terminating the process.
 * @author Dan
 *
 */
public class MoveFiles
{
	/// Setup the logging subsystem
	private transient Logger log = null ;

	/// Hook in the Common methods and values
	private transient Common common = null ;

	/// File name to which to log activities for this application.
//	private final String logFileName = "log_move_files_thread.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	private final String stopFileName = "C:\\Temp\\stop_move_files_thread.txt" ;

	/// The single instance of this class to execute the thread control
	private MoveFilesWorkerThread mkvMoveThreadWorker = null ;
	private MoveFilesWorkerThread mp4MoveThreadWorker = null ;

	/// A list of jobs for each file type to be moved.
	/// mkvMoveActionList will also include .srt files.
	protected List< MoveFileInfo > mkvMoveActionList = null ;
	protected List< MoveFileInfo > mp4MoveActionList = null  ;

	public MoveFiles( Logger log, Common common )
	{
		this.log = log ;
		this.common = common ;
		mkvMoveActionList = new ArrayList< MoveFileInfo >() ;
		mp4MoveActionList = new ArrayList< MoveFileInfo >() ;
		
		mkvMoveThreadWorker = new MoveFilesWorkerThread( log, common, mkvMoveActionList, "mkvMoveThreadWorker" ) ;
		mp4MoveThreadWorker = new MoveFilesWorkerThread( log, common, mp4MoveActionList, "mp4MoveThreadWorker" ) ;
		
		try
		{
			mkvMoveThreadWorker.start() ;
			mp4MoveThreadWorker.start() ;
		}
		catch( Exception theException )
		{
			log.warning( "Exception starting threads: " + theException.toString() ) ;
		}
	}

	public void addMKVFileMove( final String mkvFileNameWithPath, final String mkvDestinationFileNameWithPath )
	{
		if( mkvFileNameWithPath.equals( mkvDestinationFileNameWithPath ) )
		{
			// Same file, nothing to do.
			return ;
		}
		MoveFileInfo moveFileInfo = new MoveFileInfo( mkvFileNameWithPath, mkvDestinationFileNameWithPath ) ;
		synchronized( mkvMoveActionList )
		{
			mkvMoveActionList.add( moveFileInfo ) ;
		}
	}

	public void addMP4FileMove( final String mp4FileNameWithPath, final String mp4DestinationFileNameWithPath )
	{
		if( mp4FileNameWithPath.equals( mp4DestinationFileNameWithPath ) )
		{
			// Same file, nothing to do.
			return ;
		}
		MoveFileInfo moveFileInfo = new MoveFileInfo( mp4FileNameWithPath, mp4DestinationFileNameWithPath ) ;
		synchronized( mp4MoveActionList )
		{
			mp4MoveActionList.add( moveFileInfo ) ;
		}
	}

	public String getStopFileName()
	{
		return stopFileName;
	}

	/**
	 * Wait for the move threads to complete their work (action queue is empty), then
	 *  tell them to halt and wait to join.
	 */
	public void waitForThreadsToComplete()
	{
		try
		{
			log.info( "Waiting for mkvMoveThreadWorker to complete..." ) ;
			while( mkvMoveThreadWorker.hasMoreWork() )
			{
				Thread.sleep( 100 ) ;
			}
			mkvMoveThreadWorker.stopRunning() ;
			mkvMoveThreadWorker.join() ;
			log.info( "mkvMoveThreadWorker shutdown." ) ;
			
			log.info( "Waiting for mp4MoveThreadWorker to complete..." ) ;
			while( mp4MoveThreadWorker.hasMoreWork() )
			{
				Thread.sleep( 100 ) ;
			}
			mp4MoveThreadWorker.stopRunning() ;
			mp4MoveThreadWorker.join() ;
			log.info( "mp4MoveThreadWorker shutdown." ) ;
		}
		catch( Exception theException )
		{
			log.warning( "Exception: " + theException.toString() ) ;
		}
	}
	
}
