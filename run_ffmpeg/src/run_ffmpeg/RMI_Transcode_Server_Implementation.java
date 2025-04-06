package run_ffmpeg;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.logging.Logger;

import com.google.common.collect.ImmutableList;

public class RMI_Transcode_Server_Implementation extends UnicastRemoteObject implements RMI_Transcode_Server_Interface
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 7767424114076434219L;

	/// Setup the logging subsystem
	private Logger log = null ;

	/// The set of methods and variables for common use.
	private Common common = null ;

	/// File name to which to log activities for this application.
	private final String logFileName = "log_rmi_transcode_server_implementation.txt" ;

	public RMI_Transcode_Server_Implementation() throws RemoteException
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;
	}

	@Override
	public boolean transcodeFilePart( final String fileNameWithPath, final int inputFileStartTime, final int frameDuration ) throws RemoteException
	{
		final File inputFile = new File( fileNameWithPath ) ;
		log.info( "Upgrading file " + inputFile.getAbsolutePath() ) ;

		final String tmpDir = common.getPathToTmpDir() ;

		// Build the ffmpeg command
		// ffmpegCommand will hold the command to execute ffmpeg
		ImmutableList.Builder< String > ffmpegCommand = new ImmutableList.Builder<String>() ;

		// Setup ffmpeg basic options
		ffmpegCommand.add( common.getPathToFFmpeg() ) ;

		// Overwrite existing files
		ffmpegCommand.add( "-y" ) ;

		// Not exactly sure what these do but it seems to help reduce errors on some files.
		ffmpegCommand.add( "-analyzeduration", Common.getAnalyzeDurationString() ) ;
		ffmpegCommand.add( "-probesize", Common.getProbeSizeString() ) ;

		// Include source file
		ffmpegCommand.add( "-i", inputFile.getAbsolutePath() ) ;

		// Transcode to H265
		ffmpegCommand.add( "-c:v", "libx265" ) ;
		ffmpegCommand.add( "-preset", "slow" ) ;
		//			ffmpegCommand.add( "-x265-params", "lossless=1" ) ;
		ffmpegCommand.add( "-crf", "10" ) ;
		//			ffmpegCommand.add( "-tag:v", "hvc1" ) ;
		ffmpegCommand.add( "-movflags", "+faststart" ) ;
		ffmpegCommand.add( "-metadata", "-title=" + getTitle( inputFile ) ) ;

		// Copy audio and subtitles
		//			ffmpegCommand.add( "-map", "0:a" ) ;
		ffmpegCommand.add( "-c:a", "copy" ) ;
		//			ffmpegCommand.add( "-map", "0:s" ) ;
		ffmpegCommand.add( "-c:s", "copy" ) ;

		// Add output filename
		final String outputFileNameWithPath = tmpDir + "\\" + inputFile.getName() ;
		//			log.info( "outputFileNameWithPath: " + outputFileNameWithPath ) ;
		ffmpegCommand.add( outputFileNameWithPath ) ;

		final long startTime = System.nanoTime() ;
		log.info( common.toStringForCommandExecution( ffmpegCommand.build() ) ) ;

		// Only execute the transcode if testMode is false
		boolean executeSuccess = common.getTestMode() ? true : common.executeCommand( ffmpegCommand ) ;
		if( !executeSuccess )
		{
			log.warning( "Error in execute command" ) ;
			return false ;
		}

		final long endTime = System.nanoTime() ; double timeElapsedInSeconds = (endTime - startTime) / 1000000000.0 ;

		final double timePerGigaByte = timeElapsedInSeconds / (inputFile.length() / 1000000000.0) ;
		log.info( "Elapsed time to transcode "
				+ inputFile.getAbsolutePath()
				+ ": "
				+ common.getNumberFormat().format( timeElapsedInSeconds )
				+ " seconds, "
				+ common.getNumberFormat().format( timeElapsedInSeconds / 60.0 )
				+ " minutes, or "
				+ common.getNumberFormat().format( timePerGigaByte )
				+ " seconds per GB" ) ;

		return true ;
		
		// Replace the original file with the HEVC file.
//		try
//		{
//			final String origFileName = inputFile.getName() ;
//			final Path origFilePath = inputFile.toPath() ;
//
//			// First move the original file to "OLD - <origFileName>
//			final String oldFileName = "OLD " + origFileName ;
//			final String oldFileNameWithPath = common.addPathSeparatorIfNecessary( inputFile.getParent() ) + oldFileName ;
//			final File oldFile = new File( oldFileNameWithPath ) ;
//			final Path oldFilePath = oldFile.toPath() ;
//
//			log.info( "Moving " + inputFile.getAbsolutePath() + " to " + oldFilePath.toString() ) ;
//			if( !common.getTestMode() )
//			{
//				//					Files.move( origFilePath, oldFilePath ) ;
//			}
//
//			// Finally, move the temp output file to the original file
//			final File newFileInTempLocationFile = new File( outputFileNameWithPath ) ;
//			final Path newFileInTempLocationPath = newFileInTempLocationFile.toPath() ;
//
//			log.info( "Moving " + newFileInTempLocationPath.toString() + " to " + origFilePath.toString() ) ;
//			if( !common.getTestMode() )
//			{
//				//					Files.move( newFileInTempLocationPath,  origFilePath ) ;
//			}			
//		}
//		catch( Exception theException )
//		{
//			log.warning( "Error moving files: " + theException.toString() ) ;
//		}
	}

	public String getTitle( final File inputFile )
	{
		String title = "Unknown" ;
		Path thePath = Paths.get( inputFile.getAbsolutePath() ) ;

		if( inputFile.getAbsolutePath().contains( "Season " ) && !inputFile.getAbsolutePath().contains( "Season (" ) )
		{
			// TV Show
			final String tvShowEpisode = thePath.getFileName().toString() ;

			// Tokenize
			final String[] tokens = tvShowEpisode.split( " - " ) ;
			for( String token : tokens )
			{
				log.fine( "Token: " + token ) ;
			}
			if( tokens.length != 3 )
			{
				log.warning( "Invalid number of tokens (" + tokens.length + ") for file: " + inputFile.getAbsolutePath() ) ;
				return title ;
			}
			// Exactly three tokens in tvShowEpisode
			final String episodeName = tokens[ 2 ].substring( 0, tokens[ 2 ].length() - 4 ) ;
			log.fine( "episodeName: " + episodeName ) ;

			title = episodeName ;			
		}
		else
		{
			// Movie
			final String fileNameWithoutExtension = thePath.getFileName().toString().substring( 0, thePath.getFileName().toString().length() - 4 ) ;

			// Files in movie directories are of the form:
			// "Movie (2000)" or
			// "Making Of-behindthescenes"
			String fileName = fileNameWithoutExtension ;
			if( fileName.contains( "(" ) )
			{
				// String the year string (" (2000)")
				fileName = fileName.substring( 0, fileName.length() - 7 ) ;
			}
			else if( fileName.contains ( "-" ) )
			{
				final String[] tokens = fileName.split( "-" ) ;
				fileName = tokens[ 0 ] ;
			}
			else
			{
				log.warning( "Parse error on fileName: " + fileName ) ;
			}
		}		
		return title ;
	} // getTitle()
}
