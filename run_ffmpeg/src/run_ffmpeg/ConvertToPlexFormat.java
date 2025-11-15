package run_ffmpeg;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import com.google.common.collect.ImmutableList;

import run_ffmpeg.ffmpeg.FFmpeg_ProbeResult;

/**
 * Class to convert from one container type to another. Typical use is to convert from MKV or MOV to MP4
 *  to show home videos on the Plex.
 * Output files are in the Common.tmpDir.
 */
public class ConvertToPlexFormat
{
	/// Setup the logging subsystem
	private Logger log = null ;

	/// The set of methods and variables for common use.
	private Common common = null ;
	
	/// File name to which to log activities for this application.
	private static final String logFileName = "log_convert_between_containers.txt" ;
	private static final String stopFileName = "C:\\Temp\\stop_convert_between_containers.txt" ;
	
	protected final String inputExtension = "mp4" ;
	protected final String outputExtension = "mp4" ;
	protected boolean skip4KFiles = true ;
	
	public ConvertToPlexFormat()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;
	}
	
	public static void main( String[] args )
	{
		(new ConvertToPlexFormat()).execute() ;
	}
	
	public void execute()
	{
		common.setTestMode( false ) ;
		setSkip4KFiles( true ) ;
		
		log.info( "Listing files..." ) ;
		
		// Get list of input files
		List< File > inputFiles = new ArrayList< File >() ;
		inputFiles.addAll( common.getFilesInDirectoryByExtension( "C:\\Temp", getInputExtension() ) ) ;
//		inputFiles.addAll( common.getFilesInDirectoryByExtension( "\\\\skywalker\\Media\\Test", getInputExtension() ) ) ;
		// inputFiles.add( common.getFilesInDirectoryByExtension( Common.getPathToMovies() + "\\2 Fast 2 Furious (2003)", getInputExtension() ) ) ;
//		inputFiles.addAll( common.getFilesInDirectoryByExtension( Common.getPathToTVShows(),  getInputExtension() ) ) ;
		Collections.sort( inputFiles, new FileSortLargeToSmall() ) ;
		
		log.info( "Found " + inputFiles.size() + " file(s) to convert" ) ;
		
		for( File inputFile : inputFiles )
		{
			if( !shouldKeepRunning() )
			{
				break ;
			}
			
			// Skip 4K files?
			if( inputFile.getAbsolutePath().contains( "4K}" ) && doSkip4KFiles() )
			{
				// Skip this file
				log.info( "Skipping 4K file: " + inputFile.getAbsolutePath() ) ;
				continue ;
			}
			
			convertFile( inputFile ) ;
		}
		
		log.info( "Complete." ) ;
	}
	
	public void convertFile( final File inputFile )
	{
		log.info( "Converting file " + inputFile.getAbsolutePath() ) ;
		final FFmpeg_ProbeResult inputFileProbeResult = common.ffprobeFile( inputFile, log ) ;
		if( null == inputFileProbeResult )
		{
			log.warning( "Error probing file: " + inputFile.getAbsolutePath() ) ;
			return ;
		}

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

		// Not exactly sure what these do but it seems to help reduce errors on some files.
//		ffmpegCommand.add( "-analyzeduration", Common.getAnalyzeDurationString() ) ;
//		ffmpegCommand.add( "-probesize", Common.getProbeSizeString() ) ;

		// Include source file
		ffmpegCommand.add( "-i", inputFile.getAbsolutePath() ) ;

		// Transcode to H265
		ffmpegCommand.add( "-c:v", "libx265" ) ;
		//		ffmpegCommand.add( "-map", "0" ) ;
//
//		ffmpegCommand.add( "-map", "0:v" ) ;
//		ffmpegCommand.add( "-vcodec", "copy" ) ;
		
		// Copy audio and subtitles
		if( inputFileProbeResult.hasAudio() )
		{
//			ffmpegCommand.add( "-map", "0:a" ) ;
			ffmpegCommand.add( "-acodec", "copy") ;
		}

//		if( inputFileProbeResult.hasSubtitles() )
//		{
//			ffmpegCommand.add( "-map", "0:s" ) ;
//			ffmpegCommand.add( "-scodec", "copy" ) ;
//		}

		final String outputFileName = inputFile.getName().replace( "." + getInputExtension(), "." + getOutputExtension() ) ;

		// Add output filename -- it will be in tmp directory.
		final String outputFileNameWithPath = common.addPathSeparatorIfNecessary( tmpDir ) + outputFileName ;
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

		double timePerGigaByte = timeElapsedInSeconds / (inputFile.length() / 1000000000.0) ;
		log.info( "Elapsed time to transcode "
				+ inputFile.getAbsolutePath()
				+ ": "
				+ common.getNumberFormat().format( timeElapsedInSeconds )
				+ " seconds, "
				+ common.getNumberFormat().format( timeElapsedInSeconds / 60.0 )
				+ " minutes, or "
				+ common.getNumberFormat().format( timePerGigaByte )
				+ " seconds per GB" ) ;
//
//		// Replace the original file with the new file.
//		try
//		{
//			final Path origFilePath = inputFile.toPath() ;
//			final FileNamePattern fileNamePattern = new FileNamePattern( log, inputFile ) ;
//
//			// First move the original file to one that is prepended with the show or movie name
//			final String fileNameInDeleteDirectory = common.addPathSeparatorIfNecessary( Common.getPathToDeleteDir() )
//					+ fileNamePattern.getShowOrMovieOrDirectoryName()
//					+ "_" + inputFile.getName() ;
//			final File fileInDeleteDirectory = new File( fileNameInDeleteDirectory ) ;
//			final Path pathInDeleteDirectory = fileInDeleteDirectory.toPath() ;
//
//			log.info( "Moving " + origFilePath.toString() + " to " + pathInDeleteDirectory.toString() ) ;
//			if( !common.getTestMode() )
//			{
//				Files.move( origFilePath, pathInDeleteDirectory ) ;
//			}
//
//			// Finally, move the temp output file to the original file
//			final File newFileInTempLocationFile = new File( outputFileNameWithPath ) ;
//			final Path newFileInTempLocationPath = newFileInTempLocationFile.toPath() ;
//
//			log.info( "Moving " + newFileInTempLocationPath.toString() + " to " + origFilePath.toString() ) ;
//			if( !common.getTestMode() )
//			{
//				Files.move( newFileInTempLocationPath, origFilePath ) ;
//			}			
//		}
//		catch( Exception theException )
//		{
//			log.warning( "Error moving files: " + theException.toString() ) ;
//		}	
	}
	
	public void setSkip4KFiles( final boolean newValue )
	{
		skip4KFiles = newValue ;
	}
	
	public boolean doSkip4KFiles()
	{
		return skip4KFiles ;
	}

	public String getInputExtension()
	{
		return inputExtension ;
	}

	public String getOutputExtension()
	{
		return outputExtension ;
	}
	
	public static String getStopFileName()
	{
		return stopFileName ;
	}
	
	public boolean shouldKeepRunning()
	{
		return !common.shouldStopExecution( getStopFileName() ) ;
	}	
}
