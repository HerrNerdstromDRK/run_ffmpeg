package run_ffmpeg;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.logging.Logger;

import com.google.common.collect.ImmutableList;

public class RMI_Transcode_Client
{
	/// Setup the logging subsystem
	private Logger log = null ;

	/// The set of methods and variables for common use.
	private Common common = null ;

	/// File name to which to log activities for this application.
	private final String logFileName = "log_rmi_transcode_client.txt" ;

	protected final String temporarySegmentFileStorageLocationWithSeparator = "\\\\skywalker\\Media\\Test\\" ;

	public RMI_Transcode_Client()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;
	}

	public static void main( String[] args )
	{
		(new RMI_Transcode_Client()).execute() ;
	}

	public void execute()
	{
		common.setTestMode( false ) ;
		try
		{
			//			Registry registry = LocateRegistry.getRegistry( "localhost", 12345 ) ;

			final String fileNameWithPath = "\\\\skywalker\\Media\\TV_Shows\\Rick And Morty (2013)\\Season 03\\Rick And Morty - S03E106 - Inside Pickle Rick.mkv" ;
			File inputFile = new File( fileNameWithPath ) ;
			FFmpegProbeResult probeResult = common.ffprobeFile( inputFile, log ) ;
			if( null == probeResult )
			{
				log.warning( "Null probeResult for file " + fileNameWithPath ) ;
				return ;
			}
			if( (null == probeResult.streams) || probeResult.streams.isEmpty() )
			{
				log.warning( "null or empty list of streams for: " + probeResult.toString() ) ;
				return ;
			}
			FFmpegStream videoStream = probeResult.streams.get( 0 ) ;
			if( null == videoStream )
			{
				log.warning( "null videoStream for: " + probeResult.toString() ) ;
				return ;
			}
			if( !videoStream.codec_type.equalsIgnoreCase( "video" ) )
			{
				log.warning( "Stream 0 is not video: " + probeResult.toString() ) ;
				return ;
			}
			if( videoStream.tags.isEmpty() )
			{
				log.warning( "tags is empty for: " + probeResult.toString() ) ;
				return ;
			}

			transcodeByFFmpegSegments( inputFile, probeResult ) ;
			//			transcodeByFFprobeKeyFrames( inputFile ) ;

			//			RMI_Transcode_Server_Interface serverImpl = (RMI_Transcode_Server_Interface) registry.lookup( "RMI_Transcode_Server") ;
			//			log.info( "Calling transcodeFilePart()" ) ;
		}
		catch( Exception theException )
		{
			log.info( "Exception: " + theException.toString() ) ;
		}
	}

	private void transcodeByFFmpegSegments( final File inputFile, FFmpegProbeResult probeResult )
	{
		final String inputFileNameWithoutExtension = Common.getFileNameWithoutExtension( inputFile ) ;
		final String segmentWorkingDirectoryPathString = getTemporarySegmentFileStorageLocationWithSeparator()
				+ inputFileNameWithoutExtension ;
		File segmentWorkingDirectoryFile = null ;
		try
		{
			// Create the working directory
			segmentWorkingDirectoryFile = new File( segmentWorkingDirectoryPathString ) ;
			if( !segmentWorkingDirectoryFile.exists() )
			{
				segmentWorkingDirectoryFile.mkdir() ;
			}
		}
		catch( Exception theException )
		{
			log.warning( "Exception creating segmentWorkingDirectoryPathString: " + theException.toString() ) ;
			return ;
		}
		boolean createSegmentsSuccessOrFailure = createSegmentsWithFFmpeg( inputFile, segmentWorkingDirectoryFile ) ;
		if( !createSegmentsSuccessOrFailure )
		{
			log.warning( "Failed to segment file " + inputFile.getAbsolutePath() ) ;
			return ;
		}
		// File successfully segmented

		// Next, build a list of work items to transcode
		// Since a new directory is created for each file to transcode, just gather all files in the new directory
		List< File > segmentFiles = common.getFilesInDirectoryByExtension( segmentWorkingDirectoryPathString, Common.getExtension( inputFile.getAbsolutePath() ) ) ;
		log.info( "Found " + segmentFiles.size() + " file(s) in working directory " + segmentWorkingDirectoryFile.getAbsolutePath() ) ;

		// Build the RMI_Transcode_Work_Items for transcode
		List< RMI_Transcode_Work_Item > workItems = new ArrayList< RMI_Transcode_Work_Item >() ;
		for( File segmentFile : segmentFiles )
		{
			RMI_Transcode_Work_Item workItem = new RMI_Transcode_Work_Item( segmentFile, 0, 0 ) ;

			// Transcode this file segment
			final String outputFileName = common.addPathSeparatorIfNecessary( segmentWorkingDirectoryFile.getAbsolutePath() )
					+ Common.getFileNameWithoutExtension( segmentFile )
					+ "_done"
					+ "." + Common.getExtension( segmentFile.getName() ) ;
			workItem.setOutputFile( new File( outputFileName ) ) ;
			workItems.add( workItem ) ;
		}

		// Transcode each segment
		for( RMI_Transcode_Work_Item workItem : workItems )
		{
			boolean transcodeSuccess = doSegmentTranscode( workItem ) ;
			if( !transcodeSuccess )
			{
				log.warning( "Failed a transcode; aborting" ) ;
				return ;
			}
		}
		// Transcode successful

		// Combine the transcoded segments into a single file
		combineFiles( inputFile, workItems, segmentWorkingDirectoryFile, probeResult ) ;
	}

	private boolean combineFiles( final File inputFile,
			final List< RMI_Transcode_Work_Item > workItems,
			final File segmentWorkingDirectoryFile,
			final FFmpegProbeResult probeResult )
	{
		// Reassemble the files into a single file
		File concatFile = null ;
		try
		{
			concatFile = new File( common.addPathSeparatorIfNecessary( segmentWorkingDirectoryFile.getAbsolutePath() ) + "concatlist.txt" ) ;
			BufferedWriter concatFileWriter = new BufferedWriter( new FileWriter( concatFile ) ) ;
			for( RMI_Transcode_Work_Item workItem : workItems )
			{
				concatFileWriter.write( "file \'" + workItem.getOutputFile().getAbsolutePath() + "\'" + System.lineSeparator() ) ;
			}
			concatFileWriter.close() ;
		}
		catch( Exception theException )
		{
			log.warning( "Error demuxing transcode files: " + theException.toString() ) ;
		}

		ImmutableList.Builder< String > ffmpegExecuteCommand = new ImmutableList.Builder< String >() ;
		ffmpegExecuteCommand.add( common.getPathToFFmpeg() ) ;
		ffmpegExecuteCommand.add( "-f", "concat" ) ;
		ffmpegExecuteCommand.add( "-safe", "0" ) ;
		ffmpegExecuteCommand.add( "-i", concatFile.getAbsolutePath() ) ;
		ffmpegExecuteCommand.add( "-i", inputFile.getAbsolutePath() ) ;
		//		ffmpegExecuteCommand.add( "-c", "copy" ) ;
		ffmpegExecuteCommand.add( "-map", "0:v" ) ;
		ffmpegExecuteCommand.add( "-c:v", "copy" ) ;
		ffmpegExecuteCommand.add( "-map", "-1:v" ) ;
		ffmpegExecuteCommand.add( "-map", "1:a" ) ;
		if( probeResult.hasSubtitles() ) ffmpegExecuteCommand.add( "-map", "1:s" ) ;
		ffmpegExecuteCommand.add( "-c:a", "copy" ) ;
		if( probeResult.hasSubtitles() ) ffmpegExecuteCommand.add( "-c:s", "copy" ) ;
		ffmpegExecuteCommand.add( common.addPathSeparatorIfNecessary( concatFile.getParent() ) + inputFile.getName() ) ;

		boolean concatSuccessOrFailure = common.executeCommand( ffmpegExecuteCommand ) ;
		return concatSuccessOrFailure ;
	}

	private boolean createSegmentsWithFFmpeg( final File inputFile, final File segmentWorkingDirectoryFile )
	{
		final String outputFileNameBase = common.addPathSeparatorIfNecessary( segmentWorkingDirectoryFile.getAbsolutePath() )
				+ Common.getFileNameWithoutExtension( inputFile )
				+ "_%06d"
				+ "." + Common.getExtension( inputFile.getName() ) ;

		ImmutableList.Builder< String > ffmpegExecuteCommand = new ImmutableList.Builder< String >() ;
		ffmpegExecuteCommand.add( common.getPathToFFmpeg() ) ;
		ffmpegExecuteCommand.add( "-i", inputFile.getAbsolutePath() ) ;

		// Segment only the video -- including the audio (not sure on subtitles) will cause small audio delays in the
		//  concat remux that leads to audio pauses and increased video length
		// Will need to remux in audio and subtitles from the source file during concat
		ffmpegExecuteCommand.add( "-an" ) ;
		ffmpegExecuteCommand.add( "-sn" ) ;
		ffmpegExecuteCommand.add( "-codec", "copy" ) ;
		//		ffmpegExecuteCommand.add( "-flags", "+cgop" ) ;
		//		ffmpegExecuteCommand.add( "-g", "60" ) ;
		ffmpegExecuteCommand.add( "-f", "segment" ) ;
		ffmpegExecuteCommand.add( "-map", "0" ) ;
		//		ffmpegExecuteCommand.add( "-segment_list", "d:\\temp\\Test\\segment.list" ) ;
		ffmpegExecuteCommand.add( outputFileNameBase ) ;

		boolean successOrFailure = common.executeCommand( ffmpegExecuteCommand ) ;
		return successOrFailure ;
	}

	//	private void transcodeByFFprobeKeyFrames( final File inputFile )
	//	{
	//		// Build a command-delimited list of keyframes
	//		// Get the key frames
	//		final Vector< Integer > keyFrames = getKeyFrameNumbers( inputFile ) ;
	//
	//		List< RMI_Transcode_Work_Item > workItems = new ArrayList< RMI_Transcode_Work_Item >() ;
	//
	//		// Break the key frames into pairs of start and stop frames
	//		for( int keyFrameIndex = 0 ; keyFrameIndex < keyFrames.size() ; ++keyFrameIndex )
	//		{
	//			final int startFrame = keyFrames.get( keyFrameIndex ) ;
	//			int endFrame = -1 ;
	//			if( (keyFrameIndex + 1) < keyFrames.size() )
	//			{
	//				// At least one more key frame exists
	//				endFrame = keyFrames.get( keyFrameIndex + 1 ) ;
	//			}
	//
	//			RMI_Transcode_Work_Item newWorkItem = new RMI_Transcode_Work_Item( inputFile, startFrame, endFrame ) ;
	//			workItems.add( newWorkItem ) ;			
	//		} // for( keyFrameIndex )
	//		log.info( "workItems: " + workItems.toString() ) ;
	//
	//		final String fileNameWithoutExtension = Common.getFileNameWithoutExtension( inputFile ) ;
	//		for( RMI_Transcode_Work_Item workItem : workItems )
	//		{
	//			// Create the output filename
	//			//			final String segmentOutputFileNameWithStartFrame = getTemporarySegmentFileStorageLocationWithSeparator()
	//			//					+ fileNameWithoutExtension
	//			//					+ "_" + workItem.getStartFrame()
	//			//					+ "." + Common.getExtension( inputFile.getName() ) ;
	////			final String segmentFrameString = workItem.getSegmentFramesString() ;
	//			final String segmentOutputFileNameWithStartFrame = getTemporarySegmentFileStorageLocationWithSeparator()
	//					+ fileNameWithoutExtension
	//					+ "_%06d"
	//					+ "." + Common.getExtension( inputFile.getName() ) ;
	//
	//			workItem.setSegmentOutputFileNameWithPath( segmentOutputFileNameWithStartFrame ) ;
	//			doSegmentTranscode( workItem ) ;
	//		}
	//	}

	public boolean doSegmentTranscode( RMI_Transcode_Work_Item workItem )
	{		
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
		ffmpegCommand.add( "-i", workItem.inputFile.getAbsolutePath() ) ;

		// Transcode to H265
		ffmpegCommand.add( "-c:v", "libx265" ) ;
		ffmpegCommand.add( "-preset", "slow" ) ;
		//		ffmpegCommand.add( "-x265-params", "lossless=1" ) ;
		ffmpegCommand.add( "-crf", "10" ) ;
		//		ffmpegCommand.add( "-tag:v", "hvc1" ) ;
		ffmpegCommand.add( "-movflags", "+faststart" ) ;
		ffmpegCommand.add( "-metadata", "title=" + getTitle( workItem.inputFile ) ) ;

		//		log.info( "outputFileNameWithPath: " + outputFileNameWithPath ) ;
		ffmpegCommand.add( workItem.getOutputFile().getAbsolutePath() ) ;

		long startTime = System.nanoTime() ;
		log.info( common.toStringForCommandExecution( ffmpegCommand.build() ) ) ;

		// Only execute the transcode if testMode is false
		boolean executeSuccess = common.getTestMode() ? true : common.executeCommand( ffmpegCommand ) ;
		if( !executeSuccess )
		{
			log.warning( "Error in execute command" ) ;
			// Do not move any files since the transcode failed
			return false ;
		}

		long endTime = System.nanoTime() ; double timeElapsedInSeconds = (endTime - startTime) / 1000000000.0 ;

		double timePerGigaByte = timeElapsedInSeconds / (workItem.inputFile.length() / 1000000000.0) ;
		log.info( "Elapsed time to transcode "
				+ workItem.inputFile.getAbsolutePath()
				+ ": "
				+ common.getNumberFormat().format( timeElapsedInSeconds )
				+ " seconds, "
				+ common.getNumberFormat().format( timeElapsedInSeconds / 60.0 )
				+ " minutes, or "
				+ common.getNumberFormat().format( timePerGigaByte )
				+ " seconds per GB" ) ;

		boolean successOrFailure = common.executeCommand( ffmpegCommand ) ;
		return successOrFailure ;
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

	/**
	 * Return the frame number for each key frame.
	 * @param inputFile
	 * @return
	 */
	public Vector< Integer > getKeyFrameNumbers( final File inputFile )
	{
		assert( inputFile != null ) ;

		log.info( "Getting key frames for " + inputFile.getAbsolutePath() ) ;
		Vector< Integer > keyFrames = new Vector< Integer >() ;
		List< FFmpegProbeFrame > inputFrames = common.ffprobe_getVideoFrames( inputFile, log, true ) ;

		// Walk through the list of frames and store the frame number of each key frame
		int currentFrame = 0 ;
		for( FFmpegProbeFrame theFrame : inputFrames )
		{
			log.fine( theFrame.toString() ) ;
			if( null == theFrame.key_frame )
			{
				log.warning( "null key frame: " + theFrame.toString() ) ;
			}
			else
			{
				if( 1 == theFrame.key_frame.intValue() )
				{
					keyFrames.add( Integer.valueOf( currentFrame ) ) ;
				}
			}
			++currentFrame ;
		}
		log.info( "Found " + inputFrames.size() + " key frame(s)" ) ;

		//			log.info( "inputFileFrames: " + inputFileFrames.toString() ) ;
		//		for( FFmpegProbeFrame keyFrame : inputFrames )
		//		{
		//			log.info( "keyFrame: " + keyFrame.toString() ) ;
		//		}
		return keyFrames ;
	}

	public String getTemporarySegmentFileStorageLocationWithSeparator()
	{
		return temporarySegmentFileStorageLocationWithSeparator ;
	}
}
