package run_ffmpeg;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import com.google.common.collect.ImmutableList;
import com.mongodb.client.MongoCollection;

public class UpgradeMKV_TranscodeFiles
{
	/// Setup the logging subsystem
	private Logger log = null ;

	/// The set of methods and variables for common use.
	private Common common = null ;

	private MoviesAndShowsMongoDB masMDB = null ;
	private MongoCollection< FFmpegProbeResult > probeInfoCollection = null ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_upgrade_mkv_transcode_files.txt" ;	
	private static final String stopFileName = "C:\\Temp\\stop_upgrade_mkv_transcode_files.txt" ;

	public UpgradeMKV_TranscodeFiles()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;
		masMDB = new MoviesAndShowsMongoDB( log ) ;
		probeInfoCollection = masMDB.getProbeInfoCollection() ;
	}
	
	public static void main( String[] args )
	{
		(new UpgradeMKV_TranscodeFiles()).execute() ;
	}

	public void execute()
	{
		common.setTestMode( false ) ;

		MongoCollection< FFmpegProbeResult > transcodeDatabaseJobHandle = masMDB.getAction_TranscodeMKVFileInfoCollection() ;
		while( shouldKeepRunning() )
		{
			FFmpegProbeResult probeResultToTranscode = transcodeDatabaseJobHandle.findOneAndDelete( null ) ;
			if( null == probeResultToTranscode )
			{
				// Collection is empty, no work to perform
				log.info( "No more actions pending." ) ;
				break ;
			}
			// PC: probeResultToTranscode is non-null.
			upgradeFile( probeResultToTranscode ) ;
		}
		log.info( "Shutdown." ) ;
	}
	
	/**
	 * Return a MultiMap of all files that are not H265.
	 * The key is codec_name ("h264, mpeg2video, etc.), and the FFmpegProbeResult corresponds to that file.
	 * @return
	 */
	public List< FFmpegProbeResult > findFilesThatNeedUpgrade( final List< FFmpegProbeResult > allProbeInfoInstances )
	{		
		List< FFmpegProbeResult > filesToUpgrade = new ArrayList< FFmpegProbeResult >() ;
		
		for( FFmpegProbeResult theProbeResult : allProbeInfoInstances )
		{
			if( theProbeResult.isH265() || theProbeResult.isH264() )
			{
				// No upgrade necessary.
				continue ;
			}
	
			if( theProbeResult.isMP2() || theProbeResult.isVC1() )
			{
				filesToUpgrade.add( theProbeResult ) ;
				if( theProbeResult.getFileNameWithPath().contains( "Other_Videos" ) )
				{
					log.info( "Found Other_Videos with outdated codec: " + theProbeResult.getFileNameWithPath() ) ;
				}
			}
		} // while( probe....hasNext() )
		return filesToUpgrade ;
	}

	/**
	 * Return the CRF for a transcode to H265. This is dependent on the input video codec and chosen based on
	 * testing performed via CompareTranscodeOptions.
	 * @param inputFile
	 * @return
	 */
	public int getCRFToH265( final FFmpegProbeResult probeResultToTranscode )
	{
		int crf = 0 ;
		if( probeResultToTranscode.isMP2() )
		{
			crf = 7 ;
		}
		else if( probeResultToTranscode.isVC1() )
		{
			crf = 15 ;
		}
		else
		{
			log.warning( "Invalid codec " + probeResultToTranscode.getVideoCodec() + " for file " + probeResultToTranscode.getFileNameWithPath() ) ;
		}
		return crf ;
	}
	
	public static String getStopFileName()
	{
		return stopFileName ;
	}

	/**
	 * Return a Set of all video codec names. This is useful to identify which types are in the library so we can
	 *  know which we need to evaluate as things continue to change.
	 */
	public Set< String > listAllVideoCodes( final List< FFmpegProbeResult > allProbeInfoInstances )
	{
		Set< String > videoCodecNames = new HashSet< String >() ;
		
		for( FFmpegProbeResult theProbeResult : allProbeInfoInstances )
		{
			videoCodecNames.add( theProbeResult.getVideoCodec() ) ;
		}
		return videoCodecNames ;
	}

	public boolean shouldKeepRunning()
	{
		return !common.shouldStopExecution( getStopFileName() ) ;
	}

	public void upgradeFile( final FFmpegProbeResult probeResultToTranscode )
	{
		final File fileToTranscode = new File( probeResultToTranscode.getFileNameWithPath() ) ;
		log.info( "Upgrading file " + fileToTranscode.getAbsolutePath() ) ;
	
		final String tmpDir = Common.getPathToTmpDir() ;
	
		// Build the ffmpeg command
		// ffmpegCommand will hold the command to execute ffmpeg
		ImmutableList.Builder< String > ffmpegCommand = new ImmutableList.Builder< String >() ;
	
		// Setup ffmpeg basic options
		ffmpegCommand.add( common.getPathToFFmpeg() ) ;
	
		// Overwrite existing files
		ffmpegCommand.add( "-y" ) ;
	
		// Not exactly sure what these do but it seems to help reduce errors on some files.
		ffmpegCommand.add( "-analyzeduration", Common.getAnalyzeDurationString() ) ;
		ffmpegCommand.add( "-probesize", Common.getProbeSizeString() ) ;
	
		// Include source file
		ffmpegCommand.add( "-i", fileToTranscode.getAbsolutePath() ) ;
	
		// Transcode to H265
		ffmpegCommand.add( "-map", "0:v" ) ;
		ffmpegCommand.add( "-c:v", "libx265" ) ;
		ffmpegCommand.add( "-preset", "medium" ) ;
		
		final int crfValue = getCRFToH265( probeResultToTranscode ) ;
		ffmpegCommand.add( "-crf", Integer.toString( crfValue ) ) ;
		//		ffmpegCommand.add( "-tag:v", "hvc1" ) ;
	
		// Copy audio and subtitles
		ffmpegCommand.add( "-map", "0:a" ) ;
		ffmpegCommand.add( "-acodec", "copy") ;
		ffmpegCommand.add( "-map", "0:s" ) ;
		ffmpegCommand.add( "-scodec", "copy" ) ;
		
		// Set metadata
		ffmpegCommand.add( "-movflags", "+faststart" ) ;
		
		final FileNamePattern fileNamePattern = new FileNamePattern( log, fileToTranscode ) ;
		ffmpegCommand.add( "-metadata", "title=" + fileNamePattern.getTitle() ) ;
	
		// Add output filename -- it will be in tmp directory.
		final String outputFileNameWithPath = common.addPathSeparatorIfNecessary( tmpDir ) + fileToTranscode.getName() ;
		//		log.info( "outputFileNameWithPath: " + outputFileNameWithPath ) ;
		ffmpegCommand.add( outputFileNameWithPath ) ;
	
		log.info( common.toStringForCommandExecution( ffmpegCommand.build() ) ) ;
	
		long startTime = System.nanoTime() ;
	
		// Only execute the transcode if testMode is false
		boolean executeSuccess = common.getTestMode() ? true : common.executeCommand( ffmpegCommand ) ;
		if( !executeSuccess )
		{
			log.warning( "Error in execute command" ) ;
			// Do not move any files since the transcode failed
			return ;
		}
	
		long endTime = System.nanoTime() ; double timeElapsedInSeconds = (endTime - startTime) / 1000000000.0 ;
	
		double timePerGigaByte = timeElapsedInSeconds / (fileToTranscode.length() / 1000000000.0) ;
		log.info( "Elapsed time to transcode "
				+ fileToTranscode.getAbsolutePath()
				+ ": "
				+ common.getNumberFormat().format( timeElapsedInSeconds )
				+ " seconds, "
				+ common.getNumberFormat().format( timeElapsedInSeconds / 60.0 )
				+ " minutes, or "
				+ common.getNumberFormat().format( timePerGigaByte )
				+ " seconds per GB" ) ;
	
		// Replace the original file with the HEVC file.
		try
		{
			final Path origFilePath = fileToTranscode.toPath() ;
	
			// First move the original file to one that is prepended with the show or movie name
			final String fileNameInDeleteDirectory = common.addPathSeparatorIfNecessary( Common.getPathToDeleteDir() )
					+ fileNamePattern.getShowOrMovieOrDirectoryName()
					+ "_" + fileToTranscode.getName() ;
			final File fileInDeleteDirectory = new File( fileNameInDeleteDirectory ) ;
			final Path pathInDeleteDirectory = fileInDeleteDirectory.toPath() ;
	
			log.info( "Moving " + origFilePath.toString() + " to " + pathInDeleteDirectory.toString() ) ;
			if( !common.getTestMode() )
			{
				Files.move( origFilePath, pathInDeleteDirectory ) ;
			}
	
			// Finally, move the temp output file to the original file
			final File newFileInTempLocationFile = new File( outputFileNameWithPath ) ;
			final Path newFileInTempLocationPath = newFileInTempLocationFile.toPath() ;
	
			log.info( "Moving " + newFileInTempLocationPath.toString() + " to " + origFilePath.toString() ) ;
			if( !common.getTestMode() )
			{
				Files.move( newFileInTempLocationPath, origFilePath ) ;
			}			
		}
		catch( Exception theException )
		{
			log.warning( "Error moving files: " + theException.toString() ) ;
		}		
	}
}
