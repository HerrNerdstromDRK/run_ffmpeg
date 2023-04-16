package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.google.common.collect.ImmutableList;
import com.mongodb.client.MongoCollection;

/**
 * Problems to solve:
 * - Identify which movies/tv shows are missing MKVs
 * -- Build an inventory algorith to list all movies/tv shows
 * - Identify which movies/tv shows are missing subtitles
 * -- Build ffprobe for both input and output
 * --- Need means to record them
 * - Identify which movies/tv shows need forced subtitles
 * - Fix subtitles, audio, metadata for all items, if required
 * -- Need to know which are broken
 * -- Need to correlate those back to the MKV files and cross-check
 * - Build a method to update those movies/tv shows that are misconfigured or missing information
 * -- Use above database (?) of ffprobe data to analyze deltas
 * -- Update run_ffmpeg (or other) to fix those items using database inputs
 * @author Dan
 */
public class ExtractPGSFromMKVs extends Thread
{
	/// The list of drives and folders to extract. This variable is set for each thread
	/// specifically to allow multiple threads to execute in parallel but on different folders.
	private List< String > drivesAndFoldersToExtract = null ;

	/// Directory from which to read MKV files
	//	static String mkvInputDirectory = "C:\\Temp\\Little Women (2019)" ;
	private String mkvInputDirectory = "\\\\yoda\\MKV_Archive7\\Movies" ;

	/// Setup the logging subsystem
	private transient Logger log = null ;

	/// Hook in the Common methods and values
	private transient Common common = null ;
	private transient TranscodeCommon transcodeCommon = null ;

	/// File name to which to log activities for this application.
	private final String logFileName = "log_extract_pgs.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	private final String stopFileName = "C:\\Temp\\stop_pgs.txt" ;

	/// Set to true to keep the instances running, set to false otherwise.
	/// This is meant to provide a programmatic way of shutting down all of the threads.
	private transient boolean keepThreadRunning = true ;

	/// Handle to the mongodb
	private transient MoviesAndShowsMongoDB masMDB = null ;

	/// Handle to the probe info collection to lookup and store probe information for the mkv
	/// and mp4 files.
	private transient MongoCollection< FFmpegProbeResult > probeInfoCollection = null ;
	
	/// Used to maintain a consistent handle for accessing and updating the probeInfoCollection
	ProbeDirectories probeDirectories = null ;

	static final String codecTypeSubTitleNameString = "subtitle" ;
	static final String codecNameSubTitlePGSString = "hdmv_pgs_subtitle" ;
	static final String codecNameSubTitleSRTString = "subrip" ;
	static final String codecNameSubTitleDVDSubString = "dvd_subtitle" ;

	/// Identify the allowable languages for subtitles.
	static final String[] allowableSubTitleLanguages = {
			"eng",
			"en"
	} ;

	/// These subtitle codec names to be extracted.
	/// This will mostly be used when selecting which streams to extract as separate files.
	static final String[] extractableSubTitleCodecNames = {
			codecNameSubTitlePGSString,
			codecNameSubTitleSRTString
			//			codecNameSubTitleDVDSubString
	} ;

	public ExtractPGSFromMKVs()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;
		transcodeCommon = new TranscodeCommon( log, common, "", "", "", "" ) ;

		// Establish connection to the database.
		masMDB = new MoviesAndShowsMongoDB() ;
		probeInfoCollection = masMDB.getProbeInfoCollection() ;
		probeDirectories = new ProbeDirectories( log, common, masMDB, probeInfoCollection ) ;

		// The default set of drives and folders to extract is all of them.
//		setDrivesAndFoldersToExtract( common.getAllMKVDrivesAndFolders() ) ;
	}
	
	public Common getCommon()
	{
		return common ;
	}

	public ImmutableList.Builder<String> buildFFmpegSubTitleExtractionOptionsString( FFmpegProbeResult probeResult,
			TranscodeFile theFile )
	{
		// The ffmpegOptionsCommandString will contain only the options to extract subtitles
		// from the given FFmpegProbeResult
		// All of the actual ffmpeg command build ("ffmpeg -i ...") happens elsewhere
		ImmutableList.Builder<String> ffmpegOptionsCommandString = new ImmutableList.Builder<String>();

		// includedSubTitleStreams will include only allowable subtitle streams to extract
		List< FFmpegStream > extractableSubTitleStreams = findExtractableSubTitleStreams( probeResult ) ;
		for( FFmpegStream stStream : extractableSubTitleStreams )
		{
			final int streamIndex = stStream.index ;

			// So far, I know of two subtitle types I can work with: pgs and srt
			// -map 0:$streamNumber -c:s copy "$supFileName"

			// Found PGS or SRT stream
			ffmpegOptionsCommandString.add( "-map", "0:" + streamIndex ) ;

			// Create the .sup filename
			// First, replace the .mkv with empty string: Movie (2000).mkv -> Movie (2009)
			//				String outputFileName = theFile.getMKVFileNameWithPath().replace( ".mkv", "" ) ;
			String outputPath = theFile.getMKVInputDirectory() ;
			String outputFileNameWithPath = common.addPathSeparatorIfNecessary( outputPath )
					+ theFile.getMKVFileName().replace( ".mkv", "" ) ;

			// Movie (2009) -> Movie (2009).1.sup or Movie (2009).1.srt
			outputFileNameWithPath += "." + streamIndex ;
			if( stStream.codec_name.equals( codecNameSubTitlePGSString ) )
			{
				outputFileNameWithPath += ".sup" ;
			}
			else if( stStream.codec_name.equals( codecNameSubTitleSRTString ) )
			{
				outputFileNameWithPath += ".srt" ;
			}

			//			if( stStream.codec_name.equals( codecNameSubTitleDVDSubString ) )
			//			{
			//				ffmpegOptionsCommandString.add( "-c:s", "dvdsub" ) ;
			//				ffmpegOptionsCommandString.add( "-f", "rawvideo", outputFileNameWithPath ) ;
			//			}
			//			else
			//			{
			ffmpegOptionsCommandString.add( "-c:s", "copy", outputFileNameWithPath ) ;
			//			}
		}
		log.fine( "ffmpegOptionsCommandString: "
				+ common.toStringForCommandExecution( ffmpegOptionsCommandString.build() ) ) ;
		return ffmpegOptionsCommandString ;	
	}

	public void extractSubtitles( TranscodeFile fileToSubTitleExtract, FFmpegProbeResult probeResult )
	{
		// Build a set of options for an ffmpeg command based on the JSON input
		// If no suitable subtitles are found, the options string will be empty
		ImmutableList.Builder<String> subTitleExtractionOptionsString =
				buildFFmpegSubTitleExtractionOptionsString( probeResult, fileToSubTitleExtract ) ;

		// If subTitleExtractionOptionsString is empty, then no usable subtitle streams were found
		if( subTitleExtractionOptionsString.build().isEmpty() )
		{
			// No usable streams found
			// Skip this file
			return ;
		}

		// Build the ffmpeg command
		ImmutableList.Builder< String > ffmpegSubTitleExtractCommand = new ImmutableList.Builder<String>() ;
		ffmpegSubTitleExtractCommand.add( Common.getPathToFFmpeg() ) ;
		ffmpegSubTitleExtractCommand.add( "-y" ) ;
		ffmpegSubTitleExtractCommand.add( "-analyzeduration", "100M" ) ;
		ffmpegSubTitleExtractCommand.add( "-probesize", "100M" ) ;
		ffmpegSubTitleExtractCommand.add( "-i", fileToSubTitleExtract.getMKVInputFileNameWithPath() ) ;
		ffmpegSubTitleExtractCommand.addAll( subTitleExtractionOptionsString.build() ) ;

		common.executeCommand( ffmpegSubTitleExtractCommand ) ;

		// Unable to know if a subtitle stream is valid before it is written.
		// Prune any small .sup files created herein.
		PruneSmallSUPFiles pssf = new PruneSmallSUPFiles() ;
		File regularFile = new File( fileToSubTitleExtract.getMKVInputFileNameWithPath() ) ;
		pssf.pruneFolder( regularFile.getParent() ) ;
	}

	/**
	 * Walk through the list of streams, find and return the subtitle streams that can be extracted.
	 * These are generally the PGS and SRT streams, and in English only.
	 * Will always return a non-null list, although it may be empty.
	 * @param probeResult
	 * @return
	 */
	public List< FFmpegStream > findExtractableSubTitleStreams( FFmpegProbeResult probeResult )
	{
		List< FFmpegStream > extractableSubTitleStreams = new ArrayList< FFmpegStream >() ;

		for( FFmpegStream theStream : probeResult.streams )
		{
			log.fine( "Checking stream: " + theStream ) ;
			if( !theStream.codec_type.equals( codecTypeSubTitleNameString ) )
			{
				// Not a subtitle stream
				log.fine( "Ignoring stream: " + theStream ) ;
				continue ;
			}
			// Post condition: theStream is a subtitle stream

			// Check for a language tag
			if( theStream.tags.containsKey( "language" ) )
			{
				boolean isAllowableLanguage = isAllowableSubTitleLanguage( theStream.tags.get( "language" ) ) ;
				if( !isAllowableLanguage )
				{
					// Has a language tag, but it is not allowable
					log.fine( "Found subtitle with language tag but NOT allowable: "
							+ theStream.tags.get( "language" ) ) ;
					continue ;
				}
			}
			// Post condition: this is a subtitle stream with an allowable language

			if( isExtractableSubTitleCodecName( theStream.codec_name ) )
			{
				// Found allowable subtitle type
				log.info( "Found allowable subtitle stream: " + theStream ) ;
				extractableSubTitleStreams.add( theStream ) ;
			}
		} // for( stream )
		return extractableSubTitleStreams ;
	}

	public List< String > getDrivesAndFoldersToExtract()
	{
		List< String > retMe = new ArrayList< String >() ;
		if( drivesAndFoldersToExtract != null )
		{
			retMe.addAll( drivesAndFoldersToExtract ) ;
		}
		else
		{
			retMe.add( getMkvInputDirectory() ) ;
		}
		return retMe;
	}

	public String getMkvInputDirectory()
	{
		return mkvInputDirectory;
	}

	public String getStopFileName()
	{
		return stopFileName;
	}

	static boolean isAllowableSubTitleLanguage( final String audioLanguage )
	{
		for( String allowableLanguage : allowableSubTitleLanguages )
		{
			if( allowableLanguage.equalsIgnoreCase( audioLanguage ) )
			{
				// Found an allowable language
				return true ;
			}
		}
		// No allowable language found
		return false ;
	}

	static boolean isExtractableSubTitleCodecName( final String stCodeName )
	{
		for( String allowableCodecName : extractableSubTitleCodecNames )
		{
			if( allowableCodecName.equalsIgnoreCase( stCodeName ) )
			{
				// Found an allowable code name
				return true ;
			}
		}
		// No allowable code name found
		return false ;
	}

	public boolean isKeepThreadRunning()
	{
		return keepThreadRunning;
	}

	public static void main(String[] args)
	{
		boolean useThreads = false ;
		ExtractPGSFromMKVs extractPGS = new ExtractPGSFromMKVs() ;
		extractPGS.getCommon().setTestMode( false ) ;
		extractPGS.setMkvInputDirectory( "C:\\temp" ) ;
		
		/**
		 * The only difference between these two branches is that running with threads
		 * will set one chain of drives and folders to each thread and run,
		 * and running without threads will just run in a single thread.
		 * For now, running as a single thread will leave drives and folders as null,
		 * and so the mkvInputdirectory will be used.
		 */
		if( useThreads )
		{
			System.out.println( "ExtractPGSFromMKVs.main> Running with threads" ) ;
			extractPGS.runThreads() ;
		}
		else
		{
			System.out.println( "ExtractPGSFromMKVs.main> Running without threads" ) ;
			extractPGS.run() ;
		}

		System.out.println( "ExtractPGSFromMKVs.main> Shutdown." ) ;
	}

	@Override
	public void run()
	{
		List< String > drivesAndFolders = getDrivesAndFoldersToExtract() ;
		log.info( "Extracting from drives and folders: " + drivesAndFolders.toString() ) ;

		final long startTime = System.nanoTime() ;
		for( String folderName : drivesAndFolders )
		{
			log.info( "Extracting subtitle files from folder " + folderName ) ;
			if( !shouldKeepRunning() )
			{
				break ;
			}

			runOneDirectory( folderName ) ;
			log.info( "Completed extracting subtitle files from folder " + folderName ) ;
		}
		final long endTime = System.nanoTime() ;

		log.info( "Completed extracting from drives and folders: " + drivesAndFolders.toString() ) ;
		log.info( common.makeElapsedTimeString( startTime, endTime ) ) ;
		log.info( "Thread shutdown." ) ;
	}

	/**
	 * Extract from all files in a given directory.
	 * @param inputDirectory
	 */
	public void runOneDirectory( final String inputDirectory )
	{
		transcodeCommon.setMkvInputDirectory( inputDirectory ) ;

		// First, survey the input directory for files to process, and build
		// a TranscodeFile object for each.
		List< TranscodeFile > filesToProcess = transcodeCommon.surveyInputDirectoryAndBuildTranscodeFiles( inputDirectory,
				TranscodeCommon.getTranscodeExtensions() ) ;

		// Perform the core work of this application
		for( TranscodeFile theFileToProcess : filesToProcess )
		{
			runOneFile( theFileToProcess ) ;
		}
	}

	/**
	 * Extract from this one file.
	 * @param theFileToProcess
	 */
	public void runOneFile( TranscodeFile theFileToProcess )
	{
		if( !shouldKeepRunning() )
		{
			log.info( "Stopping execution due to presence of stop file: " + getStopFileName() ) ;
			return ;
		}

		// Skip this file if a .srt file exists in its directory
		if( theFileToProcess.hasSRTInputFiles() || theFileToProcess.hasSUPInputFiles() )
		{
			log.fine( "Skipping file due to presence of SRT or SUP file: " + theFileToProcess.getMKVInputFileNameWithPath() ) ;
			return ;
		}

		// Look for usable subtitle streams in the file and retrieve a list of options
		// for an ffmpeg extract command
		FFmpegProbeResult probeResult = probeDirectories.probeFileAndUpdateDB( theFileToProcess.getMKVInputFile() ) ;
		if( null == probeResult )
		{
			// Unable to ffprobe the file
			log.warning( "Error probing file: \"" + theFileToProcess.getMKVInputFileNameWithPath() + "\"" ) ;
			return ;
		}

		extractSubtitles( theFileToProcess, probeResult ) ;
	}

	/**
	 * Self-contained method that spawns two additional threads to run the extract, and keeps
	 * this thread as the controller.
	 */
	public void runThreads()
	{
		ExtractPGSFromMKVs extractPGS1 = new ExtractPGSFromMKVs() ;
		ExtractPGSFromMKVs extractPGS2 = new ExtractPGSFromMKVs() ;

		extractPGS1.setChainA() ;
		extractPGS2.setChainB() ;

		log.info( "Starting threads." ) ;
		extractPGS1.start() ;
		extractPGS2.start() ;
		log.info( "Running threads..." ) ;

		try
		{
			// Set the stop file to halt execution
			while( shouldKeepRunning()
					&& (extractPGS1.isAlive()
							|| extractPGS2.isAlive()) )
			{
				Thread.sleep( 100 ) ;
			} // while( keepRunning )
			// Post-condition: At least one condition directing this instance to halt has occurred.
			extractPGS1.stopRunningThread() ;
			extractPGS2.stopRunningThread() ;

			log.info( "Shutting down threads..." ) ;
			extractPGS1.join() ;
			extractPGS2.join() ;
		}
		catch( Exception e )
		{
			log.info( "Exception: " + e.toString() ) ;
		}
	}

	/**
	 * Tell this instance to execute only chain A.
	 */
	public void setChainA()
	{
		List< String > drivesAndFoldersToProbe = new ArrayList< String >() ;
		drivesAndFoldersToProbe.addAll( common.addToConvertToEachDrive( common.getAllChainAMKVDrivesAndFolders() ) ) ;
		drivesAndFoldersToProbe.addAll( common.getAllChainAMKVDrivesAndFolders() ) ;
		setDrivesAndFoldersToExtract( drivesAndFoldersToProbe ) ;
	}

	/**
	 * Tell this instance to execute only chain B.
	 */
	public void setChainB()
	{
		List< String > drivesAndFoldersToProbe = new ArrayList< String >() ;
		drivesAndFoldersToProbe.addAll( common.addToConvertToEachDrive( common.getAllChainBMKVDrivesAndFolders() ) ) ;
		drivesAndFoldersToProbe.addAll( common.getAllChainBMKVDrivesAndFolders() ) ;
		setDrivesAndFoldersToExtract( drivesAndFoldersToProbe ) ;
	}

	public void setDrivesAndFoldersToExtract( List< String > drivesAndFoldersToExtract )
	{
		this.drivesAndFoldersToExtract = drivesAndFoldersToExtract;
	}

	public void setMkvInputDirectory( String mkvInputDirectory )
	{
		this.mkvInputDirectory = mkvInputDirectory;
	}

	public void setKeepThreadRunning( boolean keepThreadRunning )
	{
		this.keepThreadRunning = keepThreadRunning;
	}

	public boolean shouldKeepRunning()
	{
		return (!common.shouldStopExecution( getStopFileName() ) && isKeepThreadRunning()) ;
	}

	public void stopRunningThread()
	{
		setKeepThreadRunning( false ) ;
	}

}
