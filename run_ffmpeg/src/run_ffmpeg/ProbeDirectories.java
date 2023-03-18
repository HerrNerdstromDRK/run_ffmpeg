package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

/**
 * Run ffprobe on each file in the probe directories array and record that information
 *  into the probe database.
 * @author Dan
 */
public class ProbeDirectories extends Thread
{
	/// Setup the logging subsystem
	private transient Logger log = null ;
	private transient Common common = null ;

	private transient MoviesAndShowsMongoDB masMDB = null ;
	private transient MongoCollection< FFmpegProbeResult > probeInfoCollection = null ;

	/// Store the drives and folders to probe.
	/// By default this will be all mp4 and mkv drives and folders, but can be changed below for multi-threaded use.
	private List< String > drivesAndFoldersToProbe = null ;

	/// File name to which to log activities for this application.
	private final String logFileName = "log_probe_directories.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	private final String stopFileName = "C:\\Temp\\stop_probe_directories.txt" ;

	/// The extensions of the files to probe herein.
	private final String[] extensionsToProbe = {
			".mkv",
			".mp4"
	} ;

	public ProbeDirectories()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;

		masMDB = new MoviesAndShowsMongoDB() ;
		probeInfoCollection = masMDB.getProbeInfoCollection() ;

		drivesAndFoldersToProbe = common.getAllDrivesAndFolders() ;
	}

	public ProbeDirectories( Logger log,
			Common common,
			MoviesAndShowsMongoDB masMDB,
			MongoCollection< FFmpegProbeResult > probeInfoCollection )
	{
		this.log = log ;
		this.common = common ;
		this.masMDB = masMDB ;
		this.probeInfoCollection = probeInfoCollection ;

		drivesAndFoldersToProbe = common.getAllDrivesAndFolders() ;
	}

	public static void main(String[] args)
	{
		boolean useTwoThreads = true ;
		ProbeDirectories probeDirectory = new ProbeDirectories() ;
		if( useTwoThreads )
		{
			System.out.println( "ProbeDirectories.main> Running with threads" ) ;
			probeDirectory.runThreads() ;
		}
		else
		{
			System.out.println( "ProbeDirectories.main> Running without threads" ) ;
			probeDirectory.probeDirectoriesAndUpdateDB() ;
		}
		System.out.println( "ProbeDirectories.main> Shutdown." ) ;

	}

	private void runThreads()
	{
		ProbeDirectories probeDirectories1 = new ProbeDirectories() ;
		ProbeDirectories probeDirectories2 = new ProbeDirectories() ;

		probeDirectories1.setChainA() ;
		probeDirectories2.setChainB() ;

		log.info( "Starting threads." ) ;
		probeDirectories1.start() ;
		probeDirectories2.start() ;
		log.info( "Running threads..." ) ;
		
		try
		{
			// Set the stop file to halt execution
			while( probeDirectories1.shouldKeepRunning()
					&& (probeDirectories1.isAlive()
					|| probeDirectories1.isAlive()) )
			{
				Thread.sleep( 100 ) ;
			} // while( keepRunning )

			log.info( "Shutting down threads..." ) ;
			probeDirectories1.join() ;
			probeDirectories2.join() ;
		}
		catch( Exception e )
		{
			log.info( "Exception: " + e.toString() ) ;
		}
	}

	@Override
	public void run()
	{
		probeDirectoriesAndUpdateDB() ;
	}
	
	/**
	 * Return an FFmpegProbeResult corresponding to the given fileToProbe
	 * if it exists in the database.
	 * @param probeInfoCollection
	 * @param fileToProbe
	 * @return
	 */
	public FFmpegProbeResult fileAlreadyProbed( MongoCollection< FFmpegProbeResult > probeInfoCollection, final File fileToProbe )
	{
		FFmpegProbeResult theProbeResult = null ;
		FindIterable< FFmpegProbeResult > findResult =
				probeInfoCollection.find( Filters.eq( "fileNameWithPath", fileToProbe.getAbsolutePath() ) ) ;

		Iterator< FFmpegProbeResult > findIterator = findResult.iterator() ;
		if( findIterator.hasNext() )
		{
			// Found the item in the database.
			theProbeResult = findIterator.next() ;
		}

		return theProbeResult ;
	}

	/**
	 * Return true if the probe result needs to be updated in the database.
	 * This occurs when the file:
	 *  - Exists in the database (pre-condition to call this method).
	 *  - Size has changed
	 *  - Has been updated since last probe
	 * @param fileToProbe
	 * @param probeResult
	 * @return
	 */
	public boolean needsRefresh( File fileToProbe, FFmpegProbeResult probeResult )
	{
		// Check for the special case of a missing file.
		if( fileToProbe.getName().contains( Common.getMissingFilePreExtension() ) )
		{
			// Special files never need a refresh.
			return false ;
		}

		if( fileToProbe.length() != probeResult.size )
		{
			// Size has changed
			return true ;
		}
		if( fileToProbe.lastModified() > probeResult.getLastModified() )
		{
			return true ;
		}
		return false ;
	}

	public void probeDirectoriesAndUpdateDB()
	{
		log.info( "Probing drives and folders: " + getDrivesAndFoldersToProbe() ) ;

		final long startTime = System.nanoTime() ;

		probeDirectoriesAndUpdateDB( getDrivesAndFoldersToProbe(), extensionsToProbe ) ;

		final long endTime = System.nanoTime() ;

		log.info( common.makeElapsedTimeString( startTime, endTime ) ) ;
		log.info( "Finished probing all drives and folders." ) ;
	}

	public void probeDirectoriesAndUpdateDB( final List< String > directoriesToProbe, final String[] extensions )
	{		
		// Walk through each directory
		for( String directoryToProbe : directoriesToProbe )
		{
			log.info( "Probing directory: " + directoryToProbe ) ;
			// Find files in this directory to probe
			List< File > filesToProbe = common.getFilesInDirectoryByExtension( directoryToProbe, extensions ) ;

			// Walk through each file in this directory
			for( File fileToProbe : filesToProbe )
			{
				if( !shouldKeepRunning() )
				{
					// Stop running
					log.info( "Shutting down due to presence of stop file" ) ;
					break ;
				}

				probeFileAndUpdateDB( fileToProbe ) ;
			} // for( fileToProbe )

			// Apparently rapid calls to the database creates a bunch of heap usage
			// Clear that here to prevent memory problems.
			log.info( "Cleaning garbage" ) ;
			System.gc() ;
			log.info( "Completed probing directory: " + directoryToProbe ) ;
		} // for( filesToProbe )
	}

	public FFmpegProbeResult probeFileAndUpdateDB( File fileToProbe )
	{
		return probeFileAndUpdateDB( fileToProbe, false ) ;
	}
	
	public FFmpegProbeResult probeFileAndUpdateDB( File fileToProbe, boolean forceRefresh )
	{
		// Has the file already been probed?
		FFmpegProbeResult probeResult = fileAlreadyProbed( probeInfoCollection, fileToProbe ) ;
		if( !forceRefresh && ((probeResult != null) && !needsRefresh( fileToProbe, probeResult )) )
		{
			// No need to probe again, continue to the next file.
			log.fine( "File already exists, skipping: " + fileToProbe.getAbsolutePath() ) ;
			return probeResult ;
		}
		// Post-condition: File does not currently exist in the database, or it does and it needs a refresh, or it's null

		if( probeResult != null )
		{
			// In the case it needs a refresh, just delete the old one and re-probe
			log.fine( "Deleting probeResult: " + probeResult ) ;
			probeInfoCollection.deleteOne( Filters.eq( "_id", probeResult._id ) ) ;
		}

		// File needs to a probe
		log.info( "Probing " + fileToProbe.getAbsolutePath() ) ;

		// Handle the special case that this is a missing file substitute
		if( fileToProbe.getName().contains( Common.getMissingFilePreExtension() ) )
		{
			// Missing file. Do not probe directly
			log.fine( "This is a missing file: " + fileToProbe.getAbsolutePath() );
			probeResult = new FFmpegProbeResult() ;
			probeResult.setFileNameWithPath( fileToProbe.getAbsolutePath() ) ;
			probeResult.setFileNameWithoutPath( fileToProbe.getName() ) ;
			probeResult.setFileNameShort( Common.shortenFileName( fileToProbe.getAbsolutePath() ) ) ;
			probeResult.probeTime = fileToProbe.lastModified() + 1 ;
			probeResult.chapters = new ArrayList< FFmpegChapter >() ;
			probeResult.error = new FFmpegError() ;
			probeResult.format = new FFmpegFormat() ;
			probeResult.streams = new ArrayList< FFmpegStream >() ;
		}
		else
		{
			// Probe the file with ffprobe
			probeResult = common.ffprobeFile( fileToProbe, log ) ;
		}

		// Push the probe result into the database.
		probeInfoCollection.insertOne( probeResult ) ;
		return probeResult ;
	}

	/**
	 * Tell this instance to execute only chain A.
	 */
	public void setChainA()
	{
		List< String > drivesAndFoldersToProbe = new ArrayList< String >() ;
		drivesAndFoldersToProbe.addAll( common.getAllChainAMKVDrivesAndFolders() ) ;
		drivesAndFoldersToProbe.addAll( common.getAllChainAMP4DrivesAndFolders() ) ;
		setDrivesAndFoldersToProbe( drivesAndFoldersToProbe ) ;
	}

	/**
	 * Tell this instance to execute only chain B.
	 */
	public void setChainB()
	{
		List< String > drivesAndFoldersToProbe = new ArrayList< String >() ;
		drivesAndFoldersToProbe.addAll( common.getAllChainBMKVDrivesAndFolders() ) ;
		drivesAndFoldersToProbe.addAll( common.getAllChainBMP4DrivesAndFolders() ) ;
		drivesAndFoldersToProbe.addAll( common.getMissingFiles() ) ;
		setDrivesAndFoldersToProbe( drivesAndFoldersToProbe ) ;
	}

	public List<String> getDrivesAndFoldersToProbe() {
		return drivesAndFoldersToProbe;
	}

	public void setDrivesAndFoldersToProbe(List<String> drivesAndFoldersToProbe) {
		this.drivesAndFoldersToProbe = drivesAndFoldersToProbe;
	}

	public boolean shouldKeepRunning()
	{
		return !common.shouldStopExecution( stopFileName ) ;
	}
}
