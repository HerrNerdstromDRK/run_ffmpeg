package run_ffmpeg;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
 */
public class MoveFiles extends run_ffmpegControllerThreadTemplate< MoveFilesWorkerThread >
{
	/// File name to which to log activities for this application.
	private final static String logFileName = "log_move_files.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	private final static String stopFileName = "C:\\Temp\\stop_move_files.txt" ;

	public MoveFiles( Logger log, Common common )
	{
		super( logFileName, stopFileName ) ;
	}

	/**
	 * Build the worker threads for this instance. Since we can only transcode one file at a time, just
	 *  use a single thread.
	 */
	@Override
	protected List< MoveFilesWorkerThread > buildWorkerThreads()
	{
		List< String > driveNames = common.getAllDrives() ;
		List< MoveFilesWorkerThread > workerThreads = new ArrayList< MoveFilesWorkerThread >() ;
		
		for( String driveName : driveNames )
		{
			final String threadName = common.addPathSeparatorIfNecessary( driveName ) ;

			MoveFilesWorkerThread workerThread = new MoveFilesWorkerThread( this, log, common ) ;
			workerThread.setName( threadName ) ;
			workerThreads.add( workerThread ) ;
		}
		return workerThreads ;
	}

	@Override
	public void Init()
	{}

	public void queueFileToMove( final String inputFileNameWithPath, final String outputFileNameWithPath )
	{
		assert( inputFileNameWithPath != null ) ;
		assert( !inputFileNameWithPath.isEmpty() ) ;
		assert( !inputFileNameWithPath.isBlank() ) ;
		assert( outputFileNameWithPath != null ) ;
		assert( !outputFileNameWithPath.isEmpty() ) ;
		assert( !outputFileNameWithPath.isBlank() ) ;
		
		// Find the drive with the given prefix.
		boolean foundMatch = false ;
		MoveFilesWorkerThread[] workerThreads = getWorkerThreads( new MoveFilesWorkerThread[ 0 ] ) ;
		for( MoveFilesWorkerThread workerThread : workerThreads )
		{
			final String name = workerThread.getName() ;
			if( outputFileNameWithPath.startsWith( name ) )
			{
				// Found the thread responsible for moving files onto the given drive.
				workerThread.addFileToMove( inputFileNameWithPath, outputFileNameWithPath ) ;
				foundMatch = true ;
				break ;
			}
		}
		if( !foundMatch )
		{
			log.warning( "Unable to find matching move worker thread for output file: " + outputFileNameWithPath ) ;
		}		
	}
	
	/**
	 * Quque the mkv and mp4 files to move for the given transcode file.
	 * @param theFile
	 */
	public void queueFileToMove( final TranscodeFile theFile )
	{
		assert( theFile != null ) ;
		
		queueFileToMove( theFile.getMKVInputFileNameWithPath(), theFile.getMKVFinalFileNameWithPath() ) ;
		queueFileToMove( theFile.getMP4OutputFileNameWithPath(), theFile.getMP4FinalFileNameWithPath() ) ;
	}

	/**
	 * Perform a synchronous move.
	 * @param sourcePathAndFileName
	 * @param destinationPathAndFileName
	 * @return
	 */
	public static boolean moveFile( final String sourcePathAndFileName, final String destinationPathAndFileName, Logger log, Common common )
	{
		Path moveReturn = null ;
		final File sourceFile = new File( sourcePathAndFileName ) ;
		final long fileLength = sourceFile.length() ;

		log.info( "Moving " + sourcePathAndFileName
				+ " -> " + destinationPathAndFileName ) ;

		if( !common.getTestMode() )
		{
			final long startTime = System.nanoTime() ;
			try
			{
				moveReturn = Files.move( Paths.get( sourcePathAndFileName ), Paths.get( destinationPathAndFileName ) ) ;
			}
			catch( Exception theException )
			{
				log.warning( "Unable to move file " + sourcePathAndFileName + "->" + destinationPathAndFileName
						+ ":" + theException.toString() ) ;
				moveReturn = null ;
			}

			if( moveReturn != null )
			{
				final long endTime = System.nanoTime() ;
				final double timeElapsedInSeconds = (endTime - startTime) / 1000000000.0 ;
				final double fileLengthInMB = fileLength / 1e6 ;
				final double MBPerSecond = fileLengthInMB / timeElapsedInSeconds ;

				log.info( "Successfully moved "
						+ sourcePathAndFileName 
						+ " -> "
						+ destinationPathAndFileName
						+ "; elapsed time: "
						+ common.getNumberFormat().format( timeElapsedInSeconds )
						+ " seconds, "
						+ common.getNumberFormat().format( timeElapsedInSeconds / 60.0 )
						+ " minutes; moved " + fileLengthInMB + "MB at "
						+ common.getNumberFormat().format( MBPerSecond ) + "MB/sec" ) ;
			} // if( moveReturn != null )
		} // if( testMode )
		return (moveReturn != null ? true : false) ;
	}
}
