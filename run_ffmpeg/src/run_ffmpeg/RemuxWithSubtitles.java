package run_ffmpeg;

import java.util.List;
import java.util.ArrayList ;
import java.util.Iterator;
import java.util.logging.Logger;
import java.io.File ;

import org.bson.conversions.Bson;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

public class RemuxWithSubtitles extends Thread
{
	/// Setup the logging subsystem
	private transient Logger log = null ;

	/// Hook in the Common methods and values
	private transient Common common = null ;

	/// File name to which to log activities for this application.
	private final String logFileName = "log_remux_with_subtitles.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	private final String stopFileName = "C:\\Temp\\stop_extract_ocr_transcode_move.txt" ;

	/// Move file thread controller.
	MoveFiles moveFiles = null ;

	/// Handle to the mongodb
	private transient MoviesAndShowsMongoDB masMDB = null ;

	/// Handle to the MovieAndShowInfo collection
	private transient MongoCollection< MovieAndShowInfo > movieAndShowInfoCollection = null ;

	private String driveToRemux = null ;
	private boolean keepRunning = true ;

	public RemuxWithSubtitles()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;

		// Create the MoveFiles handler, which will also start the threads.
		Logger moveFilesLogger = Common.setupLogger( "MoveFiles", MoveFiles.getLogFileName() ) ;
		moveFiles = new MoveFiles( moveFilesLogger, common ) ;

		// Establish connection to the database.
		masMDB = new MoviesAndShowsMongoDB() ;
		movieAndShowInfoCollection = masMDB.getMovieAndShowInfoCollection() ;
	}

	public static void main( String[] args )
	{
		boolean useThreads = false ;
		RemuxWithSubtitles rws = new RemuxWithSubtitles() ;
		rws.execute( useThreads ) ;
		System.out.println( "main> Shutdown." ) ;
	}

	public void execute( boolean useThreads )
	{
		// Get all of the MP4 drives
		List< RemuxWithSubtitles > remuxThreads = new ArrayList< RemuxWithSubtitles >() ;
		List< String > mp4Drives = common.getAllMP4Drives() ;
		log.info( "Remuxing " + mp4Drives.toString() + " " + (useThreads ? "with" : "without" ) + " threads" ) ;

		for( String mp4Drive : mp4Drives )
		{
			RemuxWithSubtitles rwsWorker = new RemuxWithSubtitles() ;
			rwsWorker.setDriveToRemux( mp4Drive ) ;
			remuxThreads.add( rwsWorker ) ;
		}

		if( useThreads )
		{
			log.info( "Starting threads..." ) ;
		}

		for( RemuxWithSubtitles rwsWorker : remuxThreads )
		{
			if( useThreads )
			{
				rwsWorker.start() ;
			}
			else
			{
				// Run with 0 threads
				rwsWorker.run() ;
			}
		}

		while( shouldKeepRunning() && atLeastOneThreadIsAlive( remuxThreads ) )
		{
			try
			{
				Thread.sleep( 100 ) ;
			}
			catch( Exception theException )
			{
				log.warning( "Exception: " + theException.toString() ) ;
			}
		}

		// Stop threads
		if( useThreads )
		{
			log.info( "Stopping threads..." ) ;	
			for( RemuxWithSubtitles rwsWorker : remuxThreads )
			{
				rwsWorker.setKeepRunning( false ) ;
			}
		}

		// Join threads
		if( useThreads )
		{
			log.info( "Joining threads..." ) ;	
			for( RemuxWithSubtitles rwsWorker : remuxThreads )
			{
				try
				{
					rwsWorker.join() ;
				}
				catch( Exception theException )
				{
					log.warning( "Exception: " + theException.toString() ) ;
				}
			}
		}
	}

	@Override
	public void run()
	{
		if( null == getDriveToRemux() )
		{
			log.warning( "null drive to remux" ) ;
			return ;
		}
		// Add "Movies" and "TV SHows" to the list of folders to remux
		List< String > driveToRemuxAsList = new ArrayList< String >() ;
		driveToRemuxAsList.add( getDriveToRemux() ) ;
		List< String > foldersToRemux = common.addMoviesAndFoldersToEachDrive( driveToRemuxAsList ) ;

		for( String folderToRemux : foldersToRemux )
		{
			if( shouldKeepRunning() )
			{
				remuxFolder( folderToRemux ) ;
			}
		}
	}

	private void remuxFolder( final String folderToRemux )
	{
		// Each folder should be of the form "\\\\yoda\\MP4\\Movies" (or TV Shows)
		// Use that path to search the database by mp4LongPath
		
		// Strip out all the \'s and the file server name
		// Want something like this: ".*MP4_2.*TV Shows.*" for the search string
		File yodaMP4MoviesFile = new File( folderToRemux ) ;
		File yodaMP4 = yodaMP4MoviesFile.getParentFile() ;
		final String moviesString = yodaMP4MoviesFile.getName() ;
		final String MP4String = yodaMP4.getName() ;
		
		String folderToRemuxSearchString = ".*"
				+ MP4String
				+ "\\\\.*"
				+ moviesString
				+ ".*" ;

		// Find all matching MovieAndShowInfos for this mp4LongPath
		log.info( "Running find: " + folderToRemuxSearchString ) ;

		Bson findFilesFilter = Filters.regex( "mp4LongPath", folderToRemuxSearchString ) ;
		FindIterable< MovieAndShowInfo > movieAndShowInfoFindResult = movieAndShowInfoCollection.find( findFilesFilter ) ;

		Iterator< MovieAndShowInfo > movieAndShowInfoIterator = movieAndShowInfoFindResult.iterator() ;
		while( movieAndShowInfoIterator.hasNext() )
		{
			MovieAndShowInfo theMovieAndShowInfo = movieAndShowInfoIterator.next() ;
			log.info( "Found MovieAndShowInfo: " + theMovieAndShowInfo.toString() ) ;
		}
		
	}

	public boolean atLeastOneThreadIsAlive( final List< RemuxWithSubtitles > theThreads )
	{
		for( RemuxWithSubtitles theThread : theThreads )
		{
			if( theThread.isAlive() )
			{
				return true ;
			}
		}
		return false ;
	}

	public boolean shouldKeepRunning()
	{
		return (!common.shouldStopExecution( getStopFileName() ) && isKeepRunning()) ;
	}

	protected String getDriveToRemux()
	{
		return driveToRemux;
	}

	protected void setDriveToRemux( String driveToRemux )
	{
		this.driveToRemux = driveToRemux;
	}

	protected String getStopFileName()
	{
		return stopFileName;
	}

	protected boolean isKeepRunning()
	{
		return keepRunning;
	}

	protected void setKeepRunning( boolean keepRunning )
	{
		this.keepRunning = keepRunning;
	}

}
