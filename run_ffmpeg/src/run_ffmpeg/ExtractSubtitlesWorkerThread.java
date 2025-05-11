package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.google.common.collect.ImmutableList;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.DeleteResult;

/**
 * Thread class that extracts subtitles from files in a given list of folders.
 */
public class ExtractSubtitlesWorkerThread extends run_ffmpegWorkerThread
{
	/// Reference to the controller thread.
	ExtractSubtitles theController = null ;

	/// Reference to a PD object to access its probeFileAndUpdateDB() method.
	/// Will be passed to the worker threads.
	/// Included here so only one instance of PD is created.
	private transient ProbeDirectories probeDirectories = null ;

	/// The list of folders from which to extract subtitles.
	private List< String > foldersToExtract = null ;

	/// Store the probe results by full path name for each file already probed.
	protected Map< String, FFmpegProbeResult > probedFiles = new HashMap< String, FFmpegProbeResult >() ;

	static final String codecTypeSubTitleNameString = "subtitle" ;
	static final String codecNameSubTitlePGSString = "hdmv_pgs_subtitle" ;
	static final String codecNameSubTitleSRTString = "subrip" ;
	static final String codecNameSubTitleDVDSubString = "dvd_subtitle" ;

	/// Identify the allowable languages for subtitles.
	static final String[] allowableSubTitleLanguages =
		{
				"eng",
				"en"
		} ;

	/// These subtitle codec names to be extracted.
	/// This will mostly be used when selecting which streams to extract as separate files.
	static final String[] extractableSubTitleCodecNames =
		{
				codecNameSubTitlePGSString,
				codecNameSubTitleSRTString
				//			codecNameSubTitleDVDSubString
		} ;

	/**
	 * Initiate a worker thread to extract subtitles from all the files in the given list of folders.
	 * @param theController
	 * @param log
	 * @param common
	 * @param foldersToExtract
	 */
	public ExtractSubtitlesWorkerThread( ExtractSubtitles theController,
			Logger log,
			Common common,
			ProbeDirectories probeDirectories,
			List< String > foldersToExtract )
	{
		super( log, common ) ;

		assert( theController != null ) ;
		assert( probeDirectories != null ) ;
		assert( foldersToExtract != null ) ;

		this.theController = theController ;
		this.probeDirectories = probeDirectories ;
		this.foldersToExtract = foldersToExtract ;
	}

	public ImmutableList.Builder< String > buildFFmpegSubTitleExtractionOptionsString( FFmpegProbeResult probeResult,
			TranscodeFile theFile,
			List< File > supFiles )
	{
		// The ffmpegOptionsCommandString will contain only the options to extract subtitles
		// from the given FFmpegProbeResult
		// All of the actual ffmpeg command build ("ffmpeg -i ...") happens elsewhere
		ImmutableList.Builder< String > ffmpegOptionsCommandString = new ImmutableList.Builder< String >() ;

		// includedSubTitleStreams will include only allowable subtitle streams to extract
		List< FFmpegStream > extractableSubTitleStreams = findExtractableSubTitleStreams( probeResult ) ;

		// If the file has multiple subtitle streams that can be extracted, then ensure we name the
		// first such stream without a stream index #
		boolean processedFirstStream = false ;

		for( FFmpegStream stStream : extractableSubTitleStreams )
		{
			// TEST: Looking for a way to determine if the file has a valid subtitle stream
			// Trying by checking if this subtitle stream has a tag named "NUMBER_OF_BYTES-eng" and its value is at least 250
			// -- lowest I've seen for a valid subtitle stream is 30025
			Map< String, String > tags = stStream.tags ;
			if( !tags.isEmpty() )
			{
				final String numberOfBytesEngString = tags.get( "NUMBER_OF_BYTES-eng" ) ;
				if( numberOfBytesEngString != null )
				{
					log.info( "numberOfBytesEngString: " + numberOfBytesEngString ) ;
					Integer numberOfBytesEngInteger = Integer.valueOf( numberOfBytesEngString ) ;
					if( numberOfBytesEngInteger.intValue() < 250 )
					{
						// Invalid stream?
						log.info( "Skipping subtitle stream because its number of bytes is too small: " + numberOfBytesEngString + " < 250" ) ;
						continue ;
					}
				}
			}

			//			if( stStream.duration < 1.0 )
			//			{
			//				// No valid subtitle stream
			//				log.info( "Skipping subtitle stream due to short duration: " + stStream.duration ) ;
			//				continue ;
			//			}
			final int streamIndex = stStream.index ;

			// So far, I know of two subtitle types I can work with: pgs and srt
			// -map 0:$streamNumber -c:s copy "$supFileName"

			// Found PGS or SRT stream
			ffmpegOptionsCommandString.add( "-map", "0:" + streamIndex ) ;

			// Create the .sup filename
			// First, replace the .mkv with empty string: Movie (2000).mkv -> Movie (2009)
			//				String outputFileName = theFile.getMKVFileNameWithPath().replace( ".mkv", "" ) ;
			String outputPath = theFile.getInputDirectory() ;
			String outputFileNameWithPath = common.addPathSeparatorIfNecessary( outputPath )
					+ Common.stripExtensionFromFileName( theFile.getInputFile().getName() ) ;

			// Movie (2009) -> Movie (2009).1.sup or Movie (2009).1.srt
			outputFileNameWithPath += ".en" ;
			if( extractableSubTitleStreams.size() > 1 )
			{
				// More than one stream -- include a stream id to the second and beyond streams
				if( !processedFirstStream )
				{
					// This is the first stream -- do NOT add a stream #
					processedFirstStream = true ;
				}
				else
				{
					// This is not the first subtitle stream in a file with multiple subtitle streams.
					// Add a stream index # to the filename
					outputFileNameWithPath += "." + streamIndex ;
				}
			}

			if( stStream.codec_name.equals( codecNameSubTitlePGSString ) )
			{
				outputFileNameWithPath += ".sup" ;
				supFiles.add( new File( outputFileNameWithPath ) ) ;
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

	/**
	 * Extract the subtitle streams from the given file with associated probeResult.
	 * This method will build and execute the ffmpeg command.
	 * @param fileToSubTitleExtract
	 * @param probeResult
	 */
	public void extractSubtitles( TranscodeFile fileToSubTitleExtract, FFmpegProbeResult probeResult )
	{
		// Build a set of options for an ffmpeg command based on the JSON input
		// If no suitable subtitles are found, the options string will be empty
		// supFileNames will hold the names of the sup files ffmpeg will generate
		List< File > supFiles = new ArrayList< File >() ;
		ImmutableList.Builder< String > subTitleExtractionOptionsString =
				buildFFmpegSubTitleExtractionOptionsString( probeResult, fileToSubTitleExtract, supFiles ) ;

		// If subTitleExtractionOptionsString is empty, then no usable subtitle streams were found
		if( subTitleExtractionOptionsString.build().isEmpty() )
		{
			// No usable streams found
			// Skip this file
			return ;
		}

		// Build the ffmpeg command
		ImmutableList.Builder< String > ffmpegSubTitleExtractCommand = new ImmutableList.Builder<String>() ;
		ffmpegSubTitleExtractCommand.add( common.getPathToFFmpeg() ) ;
		ffmpegSubTitleExtractCommand.add( "-y" ) ;
		ffmpegSubTitleExtractCommand.add( "-analyzeduration", Common.getAnalyzeDurationString() ) ;
		ffmpegSubTitleExtractCommand.add( "-probesize", Common.getProbeSizeString() ) ;
		ffmpegSubTitleExtractCommand.add( "-i", fileToSubTitleExtract.getInputFileNameWithPath() ) ;
		ffmpegSubTitleExtractCommand.addAll( subTitleExtractionOptionsString.build() ) ;

		boolean executeSuccess = common.executeCommand( ffmpegSubTitleExtractCommand ) ;
		if( !executeSuccess )
		{
			log.warning( "Failed to extract PGS: " + fileToSubTitleExtract.toString() ) ;
		}
		else
		{
			// Successful extract.
			// Prune the current folder for small sup files.
			PruneSmallSUPFiles pssf = new PruneSmallSUPFiles() ;
			File regularFile = new File( fileToSubTitleExtract.getInputFileNameWithPath() ) ;
			pssf.pruneFolder( regularFile.getParent() ) ;

			// Now pass any remaining sup files to the pipeline for OCR and beyond.
			List< File > remainingSupFiles = new ArrayList< File >() ;
			for( File checkFile : supFiles )
			{
				if( checkFile.exists() )
				{
					remainingSupFiles.add( checkFile ) ;
				}
				else
				{
					// .sup file was purged indicating it was too small
					probeResult.setSmallSubtitleStreams( true ) ;
				}
			}

			// If a subtitle stream was found that is too small to keep then record it in the database.
			if( probeResult.getSmallSubtitleStreams() )
			{
				// Found one more small subtitle files
				log.fine( "Found small subtitle stream for file " + fileToSubTitleExtract.getInputFileNameWithPath() ) ;

				MongoCollection< FFmpegProbeResult > probeInfoCollection = theController.getProbeInfoCollection() ;
				DeleteResult deleteResult = probeInfoCollection.deleteOne( Filters.eq( "_id", probeResult._id ) ) ;
				if( deleteResult.getDeletedCount() > 0 )
				{
					// FFmpegProbeResult was in the database; ok to insert the replacement
					// Note that the probe result could be absent from the database if we are extracting subtitles
					//  from a folder that is not kept in the database.
					probeInfoCollection.insertOne( probeResult ) ;
				}
			}

			// The addFilesToPipeline() method will handle a null pipeline.
			theController.addFilesToPipeline( remainingSupFiles ) ;
		}
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
				log.fine( "Found allowable subtitle stream: " + theStream ) ;
				extractableSubTitleStreams.add( theStream ) ;
			}
		} // for( stream )
		return extractableSubTitleStreams ;
	}

	public boolean hasMoreWork()
	{
		// Since completing one unit of work does not change the internal structures, we have no
		// way to measure if more work remains except that work is currently in progress.
		return isWorkInProgress() ;
	}

	public static boolean isAllowableSubTitleLanguage( final String audioLanguage )
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

	public static boolean isExtractableSubTitleCodecName( final String stCodeName )
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

	/**
	 * Thread main worker method to extract subtitles from files in the given list of folders.
	 */
	@Override
	public void run()
	{
		log.info( getName() + " Extracting folders: " + foldersToExtract.toString() ) ;
		final String[] extensionsToExtract = { ".mkv", ".MKV", ".mp4" } ;

		final long startTime = System.nanoTime() ;
		setWorkInProgress( true ) ;

		// Load probe info from the database for the files in the given folders.
		probedFiles.putAll( probeDirectories.getProbeInfoForFolders( foldersToExtract ) ) ;

		List< File > filesToExtract = new ArrayList< File >() ;
		for( String folderName : foldersToExtract )
		{
			filesToExtract.addAll( common.getFilesInDirectoryByExtension( folderName, extensionsToExtract ) ) ;
		}

		// Extract the largest files first to coincide with the OCR order
		Collections.sort( filesToExtract, new FileSortLargeToSmall() ) ;

		log.info( "Extracting " + filesToExtract.size() + " file(s)" ) ;
		//		for( TranscodeFile theFile : filesToProcess )
		//		{
		//			log.info( theFile.toString() ) ;
		//		}

		// Perform the core work of this application of extracting the files.
		for( File theFile : filesToExtract )
		{
			if( !shouldKeepRunning() )
			{
				log.info( getName() + " Stopping execution shouldKeepRunning returning false" ) ;
				return ;
			}
			runOneFile( theFile ) ;
		}

		log.info( getName() + " Completed extracting subtitle files from folder " + foldersToExtract ) ;

		setWorkInProgress( false ) ;
		final long endTime = System.nanoTime() ;

		log.info( getName() + " Completed extracting from drives and folders: " + foldersToExtract.toString() ) ;
		log.info( common.makeElapsedTimeString( startTime, endTime ) ) ;
		log.info( getName() + " Thread shutdown." ) ;
	} // run()

	/**
	 * Extract from this one file.
	 * @param theFileToProcess
	 */
	public void runOneFile( File inputFile )
	{
		if( !shouldKeepRunning() )
		{
			log.info( getName() + " Stopping execution shouldKeepRunning returning false" ) ;
			return ;
		}

		TranscodeFile theTranscodeFile = new TranscodeFile( inputFile, log ) ;
		// Skip this file if a .srt file exists in its directory (extract already done).
		if( theTranscodeFile.hasSRTInputFiles() || theTranscodeFile.hasSUPInputFiles() )
		{
			log.fine( getName() + " Skipping file due to presence of SRT or SUP file: " + inputFile.getAbsolutePath() ) ;
			return ;
		}

		// Look for usable subtitle streams in the file and retrieve a list of options for an ffmpeg extract command.
		FFmpegProbeResult probeResult = probedFiles.get( inputFile.getAbsolutePath() ) ;
		if( null == probeResult )
		{
			// No probe info found for the given file.
			// Probe the file
			if( inputFile.getParent().startsWith( Common.getPathToMovies() )
					|| inputFile.getParent().startsWith( Common.getPathToTVShows() ) )
			{
				// The file is in a core folder that should be recorded into the database.
				probeResult = probeDirectories.probeFileAndUpdateDB( inputFile ) ;
			}
			else
			{
				// File is not in a core folder, just probe it here and do not record into the database.
				probeResult = common.ffprobeFile( inputFile, log ) ;
			}
		}
		if( null == probeResult )
		{
			// Unable to ffprobe the file
			log.warning( "Error probing file: " + inputFile.getAbsolutePath() ) ;
			return ;
		}
		theTranscodeFile.processFFmpegProbeResult( probeResult ) ;

		extractSubtitles( theTranscodeFile, probeResult ) ;
	}

	public boolean shouldKeepRunning()
	{
		return theController.shouldKeepRunning() ;
	}
}
