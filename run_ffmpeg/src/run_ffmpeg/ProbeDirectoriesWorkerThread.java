package run_ffmpeg;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

import run_ffmpeg.ffmpeg.FFmpeg_ProbeResult;

/**
 * A worker thread for ProbeDirectories.
 */
public class ProbeDirectoriesWorkerThread extends run_ffmpegWorkerThread
{
	/// Reference back to the controller thread.
	private transient ProbeDirectories theController = null ;

	/// Handle to the database. This instance is NOT thread safe.
	private transient MongoCollection< FFmpeg_ProbeResult > probeInfoCollection = null ;

	/// This map will store all of the FFmpegProbeResults in the probeInfoCollection, keyed by the long path to the document.
	/// This instance is thread safe.
	private transient Map< String, FFmpeg_ProbeResult > probeInfoMap = new HashMap< String, FFmpeg_ProbeResult >() ;

	public ProbeDirectoriesWorkerThread( ProbeDirectories pdController,
			Logger log,
			Common common,
			MongoCollection< FFmpeg_ProbeResult > probeInfoCollection,
			Map< String, FFmpeg_ProbeResult > probeInfoMap )
	{
		super( log, common ) ;

		assert( theController != null ) ;
		assert( probeInfoCollection != null ) ;
		assert( probeInfoMap != null ) ;

		this.theController = pdController ;
		this.log = log ;
		this.common = common ;
		this.probeInfoCollection = probeInfoCollection ;
		this.probeInfoMap = probeInfoMap ;
	}

	/**
	 * Return an FFmpegProbeResult corresponding to the given fileToProbe
	 * if it exists in the database (as stored in memory).
	 * @param probeInfoCollection
	 * @param fileToProbe
	 * @return
	 */
	public FFmpeg_ProbeResult fileAlreadyProbed( final File fileToProbe )
	{
		FFmpeg_ProbeResult theProbeResult = probeInfoMap.get( fileToProbe.getAbsolutePath() ) ;
		return theProbeResult ;
	}

	/**
	 * Return true if the probe result needs to be updated in the database.
	 * This occurs when the file:
	 *  - Exists in the database (pre-condition to call this method).
	 *  and the file
	 *   - Size has changed
	 *     or
	 *   - Has been updated since last probe
	 * @param fileToProbe
	 * @param probeResult
	 * @return
	 */
	public boolean needsRefresh( File fileToProbe, FFmpeg_ProbeResult probeResult )
	{
		assert( fileToProbe != null ) ;
		assert( probeResult != null ) ;
		// TODO: Use a pre-scanned filesystem map here.

		if( fileToProbe.length() != probeResult.size )
		{
			// Size has changed
			return true ;
		}

		if( fileToProbe.lastModified() > probeResult.getLastModified() )
		{
			// File has been modified since the last probe
			return true ;
		}
		return false ;
	}

	public FFmpeg_ProbeResult probeFileAndUpdateDB( File fileToProbe )
	{
		return probeFileAndUpdateDB( fileToProbe, false ) ;
	}

	public FFmpeg_ProbeResult probeFileAndUpdateDB( File fileToProbe, boolean forceRefresh )
	{
		assert( fileToProbe != null ) ;

		// Has the file already been probed?
		FFmpeg_ProbeResult probeResult = fileAlreadyProbed( fileToProbe ) ;
		if( !forceRefresh && ((probeResult != null) && !needsRefresh( fileToProbe, probeResult )) )
		{
			// No need to probe again.
			log.fine( getName() + " File already exists and does not need a refresh, skipping: " + fileToProbe.getAbsolutePath() ) ;
			return probeResult ;
		}
		// Post-condition: File does not currently exist in the database, or it does and it needs a refresh, or it's null

		if( probeResult != null )
		{
			// In the case it needs a refresh, just delete the old one and re-probe
			log.fine( getName() + " Deleting probeResult: " + probeResult ) ;
			if( !common.getTestMode() )
			{
				synchronized( probeInfoCollection )
				{
					probeInfoCollection.deleteOne( Filters.eq( "_id", probeResult._id ) ) ;
				}
			}
		}

		// File needs a probe
		log.fine( getName() + " Probing " + fileToProbe.getAbsolutePath() ) ;

		// Probe the file with ffprobe
		probeResult = common.ffprobeFile( fileToProbe, log ) ;

		// Push the probe result into the database.
		if( probeResult != null )
		{
			if( !common.getTestMode() )
			{
				synchronized( probeInfoCollection )
				{
					try
					{
						probeInfoCollection.insertOne( probeResult ) ;
					}
					catch( Exception theException )
					{
						log.warning( "Exception: " + theException.toString() + " for probeResult: " + probeResult.toString() ) ;
					}
				}
			}
			// probeInfoMap is thread safe.
			probeInfoMap.put( probeResult.getFileNameWithPath(), probeResult ) ;
		}
		else
		{
			log.warning( getName() + " Null probeResult for file " + fileToProbe.getAbsolutePath() ) ;
		}
		return probeResult ;
	}

	@Override
	public void run()
	{
		log.info( "Running thread: " + getName() ) ;

		File fileToProbe = null ;
		while( shouldKeepRunning() && ((fileToProbe = theController.getNextFileToProbe()) != null) )
		{
			probeFileAndUpdateDB( fileToProbe ) ;
		}
	} // run()

	public boolean shouldKeepRunning()
	{
		return theController.shouldKeepRunning() ;
	}
}
