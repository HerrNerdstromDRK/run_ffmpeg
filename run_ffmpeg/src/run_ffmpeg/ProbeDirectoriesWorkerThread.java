package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

/**
 * A worker thread for ProbeDirectories.
 */
public class ProbeDirectoriesWorkerThread extends run_ffmpegWorkerThread
{
	/// Reference back to the controller thread.
	private transient ProbeDirectories theController = null ;

	private transient MongoCollection< FFmpegProbeResult > probeInfoCollection = null ;

	/// This map will store all of the FFmpegProbeResults in the probeInfoCollection, keyed by the long path to the document.
	private transient Map< String, FFmpegProbeResult > probeInfoMap = new HashMap< String, FFmpegProbeResult >() ;

	/// Store the drives and folders to probe.
	/// By default this will be all mp4 and mkv drives and folders, but can be changed below for multi-threaded use.
	private List< String > foldersToProbe = null ;

	/// The extensions of the files to probe herein.
	private final String[] extensionsToProbe = {
			".mkv",
			".mp4"
	} ;

	public ProbeDirectoriesWorkerThread( ProbeDirectories pdController,
			Logger log,
			Common common,
			MongoCollection< FFmpegProbeResult > probeInfoCollection,
			Map< String, FFmpegProbeResult > probeInfoMap,
			List< String > foldersToProbe )
	{
		super( log, common ) ;
		
		assert( theController != null ) ;
		assert( probeInfoCollection != null ) ;
		assert( probeInfoMap != null ) ;
		assert( foldersToProbe != null ) ;

		this.theController = pdController ;
		this.log = log ;
		this.common = common ;
		this.probeInfoCollection = probeInfoCollection ;
		this.probeInfoMap = probeInfoMap ;
		this.foldersToProbe = foldersToProbe ;
	}

	/**
	 * Check all existing probeInfo records against files in the file system. Remove
	 * any database entries that do not correlate to files that exist.
	 */
	protected void checkForMissingFiles()
	{
		List< File > foundFiles = new ArrayList< File >() ;

		// Iterate through each of the folders to probe. For each, find all files in the directory tree.
		for( String folderToProbe : foldersToProbe )
		{
			if( !shouldKeepRunning() )
			{
				return ;
			}
			List< File > filesInThisFolder = common.getFilesInDirectoryByExtension(
					folderToProbe, extensionsToProbe ) ;
			// getFilesInDirectoryByExtension() may return an empty List, but it is guaranteed to be non-null.
			foundFiles.addAll( filesInThisFolder ) ;
		}		
		log.info( getName() + " Found " + foundFiles.size() + " file(s)" ) ;

		// Need to walk through the probeInfoMap and check if each entry corresponds to an active file.
		// Will need to be able to search the files by path -- put the foundFiles into a map.
		Map< String, File > fileSystemMap = new HashMap< String, File >() ;
		for( File theFile : foundFiles )
		{
			if( !shouldKeepRunning() )
			{
				return ;
			}
			fileSystemMap.put( theFile.getAbsolutePath(), theFile ) ;
		}

		// Now walk through the probeInfoMap to search for missing files.
		for( Map.Entry< String, FFmpegProbeResult > entry : probeInfoMap.entrySet() )
		{
			if( !shouldKeepRunning() )
			{
				return ;
			}
			
			final String absolutePath = entry.getKey() ;
			final FFmpegProbeResult theProbeResult = entry.getValue() ;

			// Find the file in the fileSystemMap
			final File theFile = fileSystemMap.get( absolutePath ) ;
			if( null == theFile )
			{
				log.info( getName() + " Deleting missing file from database: " + absolutePath ) ;
				if( !common.getTestMode() )
				{
					probeInfoCollection.deleteOne( Filters.eq( "_id", theProbeResult._id ) ) ;
				}
			}
		}
	}

	/**
	 * Return an FFmpegProbeResult corresponding to the given fileToProbe
	 * if it exists in the database (as stored in memory).
	 * @param probeInfoCollection
	 * @param fileToProbe
	 * @return
	 */
	public FFmpegProbeResult fileAlreadyProbed( final File fileToProbe )
	{
		FFmpegProbeResult theProbeResult = probeInfoMap.get( fileToProbe.getAbsolutePath() ) ;
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
	public boolean needsRefresh( File fileToProbe, FFmpegProbeResult probeResult )
	{
		assert( fileToProbe != null ) ;
		assert( probeResult != null ) ;
		// TODO: Use a pre-scanned filesystem map here.

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
			// File has been modified since the last probe
			return true ;
		}
		return false ;
	}

	public FFmpegProbeResult probeFileAndUpdateDB( File fileToProbe )
	{
		return probeFileAndUpdateDB( fileToProbe, false ) ;
	}

	public FFmpegProbeResult probeFileAndUpdateDB( File fileToProbe, boolean forceRefresh )
	{
		assert( fileToProbe != null ) ;

		// Has the file already been probed?
		FFmpegProbeResult probeResult = fileAlreadyProbed( fileToProbe ) ;
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
				probeInfoCollection.deleteOne( Filters.eq( "_id", probeResult._id ) ) ;
			}
		}

		// File needs a probe
		log.fine( getName() + " Probing " + fileToProbe.getAbsolutePath() ) ;

		// Handle the special case that this is a missing file substitute
		if( fileToProbe.getName().contains( Common.getMissingFilePreExtension() ) )
		{
			// Missing file. Do not probe directly
			log.fine( getName() + " This is a missing file: " + fileToProbe.getAbsolutePath() );
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
		if( probeResult != null )
		{
			if( !common.getTestMode() )
			{
				probeInfoCollection.insertOne( probeResult ) ;
			}
			probeInfoMap.put( probeResult.getFileNameWithPath(), probeResult ) ;
		}
		else
		{
			log.warning( getName() + " Null probeResult for file " + fileToProbe.getAbsolutePath() ) ;
		}
		return probeResult ;
	}

	/**
	 * Walk through all files in the file system and probe any files that are not in the database.
	 */
	protected void probeNewFiles()
	{
		// Walk through each folder
		for( String folderToProbe : foldersToProbe )
		{
			if( !shouldKeepRunning() )
			{
				// Stop running
				//				log.info( "Shutting down thread" ) ;
				break ;
			}

			log.fine( getName() + " Probing folder: " + folderToProbe ) ;

			// Find files in this folder to probe
			List< File > filesToProbe = common.getFilesInDirectoryByExtension( folderToProbe, extensionsToProbe ) ;

			// Walk through each file in this folder
			for( File fileToProbe : filesToProbe )
			{
				// Be sure to check if we should shut down on a regular basis.
				if( !shouldKeepRunning() )
				{
					// Stop running
					//					log.info( getName() + " Shutting down thread" ) ;
					break ;
				}

				probeFileAndUpdateDB( fileToProbe ) ;
			} // for( fileToProbe )

			log.info( getName() + " Completed probing folder: " + folderToProbe ) ;
		} // for( folderToProbe )
	}

	@Override
	public void run()
	{
		log.info( getName() + " Probing drives and folders: " + foldersToProbe.toString() ) ;

		{
			final long startTime = System.nanoTime() ;
			log.info( getName() + " Checking for missing files" ) ;

			checkForMissingFiles() ;

			final long endTime = System.nanoTime() ;
			log.info( getName() + " Finished checking for missing files " + foldersToProbe.toString()
			+ ", " + common.makeElapsedTimeString( startTime, endTime ) ) ;
		}

		{
			final long startTime = System.nanoTime() ;

			log.info( getName() + " Probing new files" ) ;
			probeNewFiles() ;

			final long endTime = System.nanoTime() ;
			log.info( getName() + " Finished probing " + foldersToProbe.toString()
			+ ", " + common.makeElapsedTimeString( startTime, endTime ) ) ;
		}
	} // run()
	
	public boolean shouldKeepRunning()
	{
		return theController.shouldKeepRunning() ;
	}
}
