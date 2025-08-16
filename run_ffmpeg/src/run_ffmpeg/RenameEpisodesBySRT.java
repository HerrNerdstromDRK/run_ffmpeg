package run_ffmpeg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map ;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.text.similarity.CosineDistance;
import org.apache.commons.text.similarity.FuzzyScore;
import org.apache.commons.text.similarity.JaroWinklerDistance;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.apache.commons.text.similarity.LongestCommonSubsequenceDistance;

import run_ffmpeg.OpenSubtitles.OpenSubtitles;
import run_ffmpeg.OpenSubtitles.OpenSubtitles_Data;
import run_ffmpeg.OpenSubtitles.OpenSubtitles_SubtitlesResponse;
import run_ffmpeg.TheTVDB.TheTVDB;
import run_ffmpeg.TheTVDB.TheTVDB_episodeClass;
import run_ffmpeg.TheTVDB.TheTVDB_seriesEpisodesClass;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

/**
 * This class matches and renames .mkv files for tv shows to their proper series/season/episode names. It also updates the names
 *  of the .srt files.
 * It downloads reference srt files from opensubtitles and compares those against the srt files for the .mkv files
 * Downloading reference srt files is throttled by opensubtitles, but this application includes wait operations to eventually
 *  download them all.
 * Precondition: The .mkv files MUST already have one srt file for each (use Subtitles_Orchestrator).
 */
public class RenameEpisodesBySRT
{
	/// Setup the logging subsystem
	protected Logger log = null ;
	protected Common common = null ;
	protected BufferedWriter dataFileWriter = null ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_rename_episodes_by_srt.txt" ;
	private static final String stopFileName = "C:\\Temp\\stop_rename_episodes_by_srt.txt" ;
	private static final String matchingDataFileName = "data_ai_episode_matching.csv" ;

	/// The number of minutes to match against in the srt files.
	/// Based on testing, 4 minutes using Cosine Distance seems to be a good starting point for matching.
	protected static final int numMinutesToMatch = 4 ;

	protected OpenSubtitles openSubtitles = null ;
//	protected OpenAIWhisper whisper = null ;
	protected TheTVDB theTVDB = null ;

	public RenameEpisodesBySRT()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;

		initObject() ;
	}

	public RenameEpisodesBySRT( Logger log, Common common )
	{
		this.log = log ;
		this.common = common ;

		initObject() ;
	}

	private void initObject()
	{
		log.info( "Logging into opensubtitles" ) ;
		openSubtitles = new OpenSubtitles( log, common ) ;

//		whisper = new OpenAIWhisper( log, common ) ;

		log.info( "Logging into tvdb" ) ;
		theTVDB = new TheTVDB( log, common ) ;

		openDataFile() ;
	}

	public static void main( String[] args )
	{
		(new RenameEpisodesBySRT()).execute() ;
	}

	public void execute()
	{
		common.setTestMode( false ) ;

		// The number of SRT files we can download is limited by time block (at present 20 downloads per 4-hour block).
		// However, the AI portion of this algorithm takes a long time.
		// -> Download SRT files whenever possible, and work the other actions otherwise.
		// Generalize this to queue all work ahead of time and then do one unit of work each iteration while checking for
		//  download time expiration.
		List< RenameEpisodesBySRT_DownloadDataClass > subtitleDataToDownload = new ArrayList< RenameEpisodesBySRT_DownloadDataClass >() ;

		// Can add shows or seasons individually
		// showDirectories is the set of tv show directories, one directory for each show
		List< File > showDirectories = new ArrayList< File >() ;
		//		showDirectories.addAll( getShowDirectories( Common.getPathToToOCR() ) ) ;
		showDirectories.add( new File( "\\\\skywalker\\Media\\To_OCR\\Greys Anatomy (2005) {imdb-0413573} {tvdb-73762}" ) ) ;

		// seasonDirectories will be populated with each season of each show listed in the showDirectories structure.
		// Can also add individual seasons directly into the list.
		List< File > seasonDirectories = new ArrayList< File >() ;
		seasonDirectories.addAll( getSeasonDirectoriesFromShowDirectories( showDirectories ) ) ;

		Map< File, TheTVDB_seriesEpisodesClass > seasonDirectoryEpisodes = new HashMap< File, TheTVDB_seriesEpisodesClass >() ;

		// Walk through the seasonDirectories and identify which subtitles to download and find the generated srt files
		setupPreprocessingWork( seasonDirectories, subtitleDataToDownload, seasonDirectoryEpisodes ) ;

		// Download the subtitle files
		conductPreprocessingWork( subtitleDataToDownload ) ;

		// The previous method can complete either by completing the preprocessing work or because the stop file is now present
		// indicating we should stop the application.
		if( !shouldKeepRunning() )
		{
			log.info( "Halting due to presence of stop file." ) ;
			return ;
		}

		// Done, hopefully, with all the data gathering.
		// Perform the matching.
		conductMatching( seasonDirectories, seasonDirectoryEpisodes ) ;
		
		// doCleanUp() ;

		closeDataFile() ;
		log.info( "Done." ) ;
	} // execute()

	protected void closeDataFile()
	{
		try
		{
			dataFileWriter.close() ;
		}
		catch( Exception theException )
		{
			log.warning( "Failed to close data file: " + theException.toString() ) ;
		}
	}

	public void conductMatching( final List< File > seasonDirectories, final Map< File, TheTVDB_seriesEpisodesClass > seasonDirectoryEpisodes )
	{
		for( File seasonDirectory : seasonDirectories )
		{
			log.info( "Processing show season " + seasonDirectory.getAbsolutePath() + "..." ) ;
	
			final String imdbShowIDString = FileNamePattern.getIMDBShowID( seasonDirectory ) ;
			assert( imdbShowIDString != null ) ;
	
			final String tvdbShowIDString = FileNamePattern.getTVDBShowID( seasonDirectory ) ;
			assert( tvdbShowIDString != null ) ;
	
			//				final int seasonNumber = FileNamePattern.getShowSeasonNumber( seasonDirectory ) ;
	
			final List< File > downloadedSRTFiles = common.getFilesInDirectoryByRegex( seasonDirectory, "[\\d]+ - S[\\d]+E[\\d]+ - downloaded.srt" ) ;
			final List< File > generatedSRTFiles = common.getFilesInDirectoryByRegex( seasonDirectory, "^(?!.*downloaded).*\\.srt$" ) ;
	
			// Extract the first several minutes of subtitle text from each file to avoid reading each generated srt file multiple times.
			List< RenameEpisdodesBySRT_SRTData > downloadedSRTFilesData = new ArrayList< RenameEpisdodesBySRT_SRTData >() ;
			for( File downloadedSRTFile : downloadedSRTFiles )
			{
				final String firstMinutesOfSubtitleText = getFirstMinutesOfSRTFile( downloadedSRTFile, getNumMinutesToMatch() ) ;
				RenameEpisdodesBySRT_SRTData srtFileData = new RenameEpisdodesBySRT_SRTData( downloadedSRTFile, firstMinutesOfSubtitleText ) ;
				downloadedSRTFilesData.add( srtFileData ) ;
			}
	
			List< RenameEpisdodesBySRT_SRTData > generatedSRTFilesData = new ArrayList< RenameEpisdodesBySRT_SRTData >() ;
			for( File generatedSRTFile : generatedSRTFiles )
			{
				final String firstMinutesOfSubtitleText = getFirstMinutesOfSRTFile( generatedSRTFile, getNumMinutesToMatch() ) ;
				RenameEpisdodesBySRT_SRTData srtFileData = new RenameEpisdodesBySRT_SRTData( generatedSRTFile, firstMinutesOfSubtitleText ) ;
				generatedSRTFilesData.add( srtFileData ) ;
			}
	
			// Find the best match between the downloaded srt file and the generated srt file. The generated srt file
			// is built from a similarly named mkv file.
			// Once identified, rename the mkv file to the show episode following the pattern of the downloaded srt file
			// and rename the downloaded srt file with the correct show name and episode name.
			for( RenameEpisdodesBySRT_SRTData downloadedSRTFileData : downloadedSRTFilesData )
			{
				final File downloadedSRTFile = downloadedSRTFileData.getSrtFile() ;
				final File bestGeneratedSRTMatchFile = getBestSRTMatch( downloadedSRTFileData, generatedSRTFilesData ) ;
	
				// Get the proper show name.
				final Pattern showNamePattern = Pattern.compile( "(?<showName>.*) \\([\\d]+\\) \\{(imdb|tvdb)-[\\d]+\\}" ) ;
				final String matcherShowNameString = bestGeneratedSRTMatchFile.getParentFile().getParentFile().getName() ;
				final Matcher showNameMatcher = showNamePattern.matcher( matcherShowNameString ) ;
				if( !showNameMatcher.find() )
				{
					log.warning( "Unable to locate show name from path: " + bestGeneratedSRTMatchFile.getAbsolutePath() ) ;
					continue ;
				}
				
				// Now have the best match for the downloadedSRTFile
				// Get the season and episode number.
				final File mkvFromDownloadedSRTFile = new File( downloadedSRTFile.getAbsolutePath().replace( ".srt", ".mkv" ) ) ;
				FileNamePattern fnp = new FileNamePattern( log, mkvFromDownloadedSRTFile ) ;
				
				final String showName = showNameMatcher.group( "showName" ) ;
				final int seasonNumber = fnp.getSeasonNumber() ;
				final int episodeNumber = fnp.getEpisodeNumber() ;
				final String episodeName = getEpisodeName( seasonDirectoryEpisodes.get( downloadedSRTFile.getParentFile() ), seasonNumber, episodeNumber ) ;
				String baseFileName = showName + " - S" ;
				if( seasonNumber < 10 ) baseFileName += "0" ;
				baseFileName += Integer.toString( seasonNumber ) ;
				baseFileName += "E" ; // Episode
				if( episodeNumber < 10 ) baseFileName += "0" ;
				baseFileName += Integer.toString( episodeNumber ) ;
				baseFileName += " - "
						+ episodeName ;
	
				final String outputMKVFileName = baseFileName + ".mkv" ;
				final String outputSRTFileName = baseFileName + ".en.srt" ;
				final File outputMKVFile = new File( downloadedSRTFile.getParentFile(), outputMKVFileName ) ;
				final File outputSRTFile = new File( downloadedSRTFile.getParentFile(), outputSRTFileName ) ;
				
				final String inputMKVFileName = bestGeneratedSRTMatchFile.getAbsolutePath().replace( ".srt", ".mkv" ) ;
				final File inputMKVFile = new File( downloadedSRTFile.getParentFile(), inputMKVFileName ) ;
				
				// Replace the original .mkv file with the new name, and the same for the downloaded srt file
				try
				{
					log.info( "Renaming " + bestGeneratedSRTMatchFile.getAbsolutePath() + " -> " + outputSRTFile.getAbsolutePath() ) ;
					log.info( "Renaming " + inputMKVFile.getAbsolutePath() + " -> " + outputMKVFile.getAbsolutePath() ) ;
				}
				catch( Exception theException )
				{
					log.warning( "Error renaming a file: " + theException.toString() ) ;
				}
			}
		} // for( seasonDirectory )
	} // performMatching()

	public void conductPreprocessingWork( final List< RenameEpisodesBySRT_DownloadDataClass > subtitleDataToDownload )
	{
		// Main loop for processing data items
		// Will only conduct one unit of work per loop to minimize time between subtitle file downloads
		boolean didWorkThisLoop = false ;
		while( shouldKeepRunning() && !subtitleDataToDownload.isEmpty() )
		{
			// This loop is designed to do all srt downloads permitted, followed by one wav extraction,
			//  followed by one ai srt generation. However, so long as any srt files remain to be downloaded,
			//  at most one of wav extraction or ai srt generation will occur per loop. This permits time to
			//  cycle back to the download algorithm to minimize time we miss while the reset time is expired.

			// Track if work was accomplished this loop.
			// If no work was accomplished, then we are waiting for the download time to expire.
			didWorkThisLoop = false ;

			if( openSubtitles.isDownloadAllowed() && !subtitleDataToDownload.isEmpty() )
			{
				// Download next SRT file
				final RenameEpisodesBySRT_DownloadDataClass downloadData = subtitleDataToDownload.removeFirst() ;
				final long downloadResponse = downloadSRTFile( downloadData, openSubtitles ) ;
				if( downloadResponse < 0 )
				{
					// Since this download failed, place it back in the list for a future attempt.
					subtitleDataToDownload.add( 0, downloadData ) ;

					didWorkThisLoop = true ;
				}

				// Skip the rest of the loop so the next loop can start on downloading subtitles.
				continue ;
			}
			// PC: Downloads are throttled or no more srt files to download.

			if( !didWorkThisLoop )
			{
				// Nothing done this loop...must be waiting for timer to expire.
				try
				{
					Thread.sleep( 1000 ) ;
				}
				catch( Exception theException )
				{
					log.warning( "Exception during sleep(): " + theException.toString() ) ;
				}
			}
		} // while( shouldKeepRunning() )
	}

	/**
	 * Download the subtitle file identified by subtitleData into outputDirectory.
	 * @param subtitleData
	 * @param openSubtitles
	 * @param outputDirectory
	 * @return 0 if successful, the next time (in milliseconds) when another download may occur otherwise.
	 */
	public long downloadSRTFile( final RenameEpisodesBySRT_DownloadDataClass downloadData, OpenSubtitles openSubtitles )
	{
		assert( downloadData != null ) ;
	
		final OpenSubtitles_Data subtitleData = downloadData.subtitleData ;
		final int seasonNumber = downloadData.getSeasonNumber() ;
		final int episodeNumber = downloadData.getEpisodeNumber() ;
		final String subtitleFileID = downloadData.subtitleData.getAttributes().getFiles().getFirst().getFile_id().toString() ;
	
		final String newFileName = makeSRTDownloadFileName( downloadData.getImdbShowIDString(),
				seasonNumber,
				episodeNumber ) ;
		final File newOutputFile = new File( downloadData.getOutputDirectory(), newFileName ) ;
		if( newOutputFile.exists() )
		{
			// File already exists, no need to download.
			return 0 ;
		}
	
		File subtitleFile = openSubtitles.downloadSubtitleFileByID( subtitleFileID, newOutputFile ) ;
		if( null == subtitleFile )
		{
			log.warning( "Failed to download file " + subtitleData.getAttributes().getFiles().getFirst().getFile_name() ) ;
			return -1 ;
		}
	
		return 0 ;
	}

	public void downloadSRTFilesForSeason( final String imdbShowIDString, final int seasonNumber, final File outputDirectory )
	{
		OpenSubtitles openSubtitles = new OpenSubtitles( log, common ) ;
	
		// First, get all subtitle information for this show and season
		List< OpenSubtitles_Data > allSubtitlesForSeason = openSubtitles.getSubtitleInfoForShowSeason( imdbShowIDString, seasonNumber ) ;
		assert( allSubtitlesForSeason != null ) ;
	
		// Next, find the best subtitles for each episode
		final List< OpenSubtitles_Data > subtitleDataToDownload = openSubtitles.findBestSubtitleFileIDsToDownloadForSeason( allSubtitlesForSeason ) ;
		assert( subtitleDataToDownload != null ) ;
	
		// Download each subtitle file and rename to match this convention:
		// imdbShowID - SXXEYY - downloaded.srt
		List< File > downloadedSubtitleFiles = new ArrayList< File >() ;
		for( OpenSubtitles_Data subtitleFileData : subtitleDataToDownload )
		{
			final String subtitleFileID = subtitleFileData.getAttributes().getFiles().getFirst().getFile_id().toString() ;
			final int episodeNumber = subtitleFileData.getAttributes().getFeature_details().getEpisode_number().intValue() ;
			final String newFileName = makeSRTDownloadFileName( imdbShowIDString, seasonNumber, episodeNumber ) ;
			final File outputFile = new File( outputDirectory, newFileName ) ;
	
			File subtitleFile = openSubtitles.downloadSubtitleFileByID( subtitleFileID, outputFile ) ;
			if( null == subtitleFile )
			{
				log.warning( "Failed to download file " + subtitleFileData.getAttributes().getFiles().getFirst().getFile_name() ) ;
				continue ;
			}
	
			downloadedSubtitleFiles.add( subtitleFile ) ;
		}
	}

	public List< File > findDownloadedSRTFiles( final File inputDirectory )
	{
		assert( inputDirectory != null ) ;
		assert( inputDirectory.isDirectory() ) ;
	
		List< File > downloadedSRTFiles = new ArrayList< File >() ;
		final File[] fileList = inputDirectory.listFiles() ;
	
		final Pattern downloadedSRTFilePattern = Pattern.compile( "[\\d]+ - S[\\d]+E[\\d]+ - downloaded.srt" ) ;
		for( File theFile : fileList )
		{
			final Matcher downloadedSRTFileMatcher = downloadedSRTFilePattern.matcher( theFile.getName() ) ;
			if( downloadedSRTFileMatcher.find() )
			{
				// Found a match.
				downloadedSRTFiles.add( theFile ) ;
			}
		}
		return downloadedSRTFiles ;		
	}

	public Set< Integer > findMissingDownloadedSRTEpisodes( final String imdbShowIDString, final File seasonDirectory, final TheTVDB_seriesEpisodesClass seasonEpisodes )
	{
		Set< Integer > missingDownloadedSRTEpisodes = new HashSet< Integer >() ;
	
		for( TheTVDB_episodeClass seasonEpisode : seasonEpisodes.data.episodes )
		{
			final String downloadedSRTFileName = makeSRTDownloadFileName( imdbShowIDString, seasonEpisode.seasonNumber.intValue(), seasonEpisode.number.intValue() ) ;
			final File downloadedSRTFile = new File( seasonDirectory, downloadedSRTFileName ) ;
			if( !downloadedSRTFile.exists() )
			{
				// Missing file.
				missingDownloadedSRTEpisodes.add( seasonEpisode.number ) ;
			}
		}
		return missingDownloadedSRTEpisodes ;
	}

	/**
	 * Find the best matching SRT file from the list of generatedSRTFilesData against downloadedSRTFileData.
	 * @param downloadedSRTFileData
	 * @param generatedSRTFilesData
	 */
	public File getBestSRTMatch( final RenameEpisdodesBySRT_SRTData downloadedSRTFileData,
			final List< RenameEpisdodesBySRT_SRTData > generatedSRTFilesData )
	{
		assert( downloadedSRTFileData != null ) ;
		assert( generatedSRTFilesData != null ) ;
		assert( !generatedSRTFilesData.isEmpty() ) ;
	
		File bestMatchFile = null ;
		Double bestMatchDouble = null ;
	
		final String firstMinutesOfDownloadedSRTFile = downloadedSRTFileData.getFirstMinutesOfSubtitleText() ;
		CosineDistance cosineDistance = new CosineDistance() ; // Lower is better
	
		for( RenameEpisdodesBySRT_SRTData generatedSRTFileData : generatedSRTFilesData )
		{
			final String firstMinutesOfGeneratedSRTFile = generatedSRTFileData.getFirstMinutesOfSubtitleText() ;
			final Double cosineDistanceDouble = cosineDistance.apply( firstMinutesOfDownloadedSRTFile, firstMinutesOfGeneratedSRTFile ) ;
	
			if( (null == bestMatchFile) || (cosineDistanceDouble.doubleValue() < bestMatchDouble.doubleValue()) )
			{
				// No matches yet or this is a better match.
				bestMatchFile = generatedSRTFileData.getSrtFile() ;
				bestMatchDouble = cosineDistanceDouble ;
			}
		}
		return bestMatchFile ;
	}

	public String getEpisodeName( final TheTVDB_seriesEpisodesClass seasonDirectoryEpisodes, final int seasonNumber, final int episodeNumber )
	{
		assert( seasonDirectoryEpisodes != null ) ;
		
		String episodeName = "NOT FOUND" ;
		for( TheTVDB_episodeClass theEpisode : seasonDirectoryEpisodes.data.episodes )
		{
			if( (seasonNumber == theEpisode.seasonNumber.intValue()) && (episodeNumber == theEpisode.number) )
			{
				episodeName = theEpisode.name ;
				break ;
			}
		}
		
		return episodeName ;
	}

	public String getFirstMinutesOfSRTFile( final File inputFile, final int numMinutes )
	{
		assert( inputFile != null ) ;
		final long MINUTES_IN_MILLIS = numMinutes * 60 * 1000 ; // milliseconds
	
		StringBuilder result = new StringBuilder() ;
		try( BufferedReader reader = new BufferedReader(
				new FileReader( inputFile.getAbsolutePath() ) ) )
		{
			String line = null ;
			//			long currentTimeMillis = 0;
	
			while( (line = reader.readLine()) != null )
			{
				// Skip blank lines
				if( line.trim().isEmpty() )
				{
					continue ;
				}
	
				// SRT files are broken into blocks that each look like this:
				// 1 // the segment number
				// 00:00:12,923 --> 00:00:14,987 // start and end time for this subtitle
				// The following takes place between 5 a.m. // subtitle text
	
				// Check for subtitle number
				if( isInteger( line.trim() ) )
				{
					// Got the beginning of a segment
					// Read the timecode line
					final String timecodeLine = reader.readLine() ;
					if( timecodeLine != null )
					{
						final long startTimeMillis = parseTimecode( timecodeLine.split( "-->" )[ 0 ].trim() ) ;
	
						// If the subtitle starts within the first two minutes
						if( startTimeMillis < MINUTES_IN_MILLIS )
						{
							// Read subtitle text until a blank line
							while( (line = reader.readLine()) != null && !line.trim().isEmpty() )
							{
								result.append( line ).append(" ") ;
							}
							result.append( System.lineSeparator() ) ; // Add a newline after each subtitle
						}
						else
						{
							// If the subtitle starts after two minutes, we can stop processing
							break ; 
						} // if( startTimeMillis < 2min )
					} // if( timecodeLine != null )
				} // if( isInteger )
			} // while( line = reader )
		} // try
		catch( Exception theException )
		{
			log.warning( "Exception: " + theException.toString() ) ;
		}
		return result.toString().trim() ;
	}

	public static String getMatchingDataFilename()
	{
		return matchingDataFileName ;
	}

	public int getNumMinutesToMatch()
	{
		return numMinutesToMatch ;
	}

	public List< File > getSeasonDirectoriesFromShowDirectories( final List< File > showDirectories )
	{
		List< File > seasonDirectories = new ArrayList< File >() ;
	
		// Sort through the showDirectories and find seasonDirectories
		for( File showDirectory : showDirectories )
		{
			log.info( "Processing show " + showDirectory.getAbsolutePath() + "..." ) ;
	
			final String imdbShowIDString = FileNamePattern.getIMDBShowID( showDirectory ) ;
			if( null == imdbShowIDString )
			{
				log.info( "The path " + showDirectory.getAbsolutePath() + " is not a tv show" ) ;
				// This is a non-fatal error as the To_OCR directory may have movies also.
				continue ;
			}
	
			final File[] showDirectoryFiles = showDirectory.listFiles() ;
			assert( showDirectoryFiles != null ) ;
	
			for( File showDirectoryFile : showDirectoryFiles )
			{
				if( !showDirectoryFile.isDirectory() )
				{
					// Not a directory.
					continue ;
				}
				// PC: It's a directory
				final int seasonNumber = FileNamePattern.getShowSeasonNumber( showDirectoryFile ) ;
				if( seasonNumber != -1 )
				{
					// It's a valid season number.
					seasonDirectories.add( showDirectoryFile ) ;
				}
			}
		} // for( showDirectory )
		// PC: seasonDirectories has all valid show season files
	
		return seasonDirectories ;
	}

	public List< File > getShowDirectories( final String topLevelDirectoryString )
	{
		assert( topLevelDirectoryString != null ) ;
	
		List< File > showDirectories = new ArrayList< File >() ;
		final File topLevelDirectoryFile = new File( topLevelDirectoryString ) ;
		final File[] topLevelSubDirectories = topLevelDirectoryFile.listFiles() ;
	
		// Iterate through the directories.
		// Some of them may be movie directories -- skip those and only keep the show directories.
	
		// Each show directory has an imdb or tvdb identifier in the path name.
		// Look for one of those.
		final Pattern showTagPattern = Pattern.compile( ".*\\{(imdb|tvdb)-[\\d]+\\}.*Season .*" ) ;
	
		for( File subDirectory : topLevelSubDirectories )
		{
			// Need to see the full path that includes the "Season XX" directory, which means I need to resolve
			// down to the file level.
	
			// Find all files under this subdirectory
			final List< File > subDirectoryFiles = common.getFilesInDirectoryByExtension( subDirectory, Common.getVideoExtensions() ) ;
			for( File testFile : subDirectoryFiles )
			{
				final Matcher showTagMatcher = showTagPattern.matcher( testFile.getAbsolutePath() ) ;
				if( showTagMatcher.find() )
				{
					// Found a tv show
					showDirectories.add( subDirectory ) ;
					break ;
				}
			}
		}
		return showDirectories ;
	}

	public String getStopFileName()
	{
		return stopFileName ;
	}

	private static boolean isInteger( final String s )
	{
		try
		{
			Integer.parseInt( s ) ;
			return true ;
		}
		catch( NumberFormatException e )
		{
			return false ;
		}
	}

	public String makeSRTDownloadFileName( final String imdbShowIDString,
			final int seasonNumber,
			final int episodeNumber )
	{
		final String srtDownloadFileName = imdbShowIDString
				+ " - S" + (seasonNumber < 10 ? "0" : "") + seasonNumber
				+ "E" + (episodeNumber < 10 ? "0" : "" ) + episodeNumber
				+ " - downloaded.srt" ;
		return srtDownloadFileName ;
	}

	protected void openDataFile()
	{
		try
		{
			dataFileWriter = new BufferedWriter( new FileWriter( getMatchingDataFilename() ) ) ;
	
			// dataFileWriter format:
			// downloadedsrtfilename,generatedsrtfilename,fuzzy score,levenshtein,longestcommonsubsequence,cosine distance,jarowinkler distance,levensthein+longestcommon
			dataFileWriter.write( "Downloaded SRT File Name,Generated SRT File Name, Fuzzy Logic Score, Levensthein Distance,Longest Common Subsequence Distance"
					+ ",Cosine Distance,Jaro Winkler Distance,Levenshtein + Longest Common Subsequence Distance" ) ;
			dataFileWriter.write( System.lineSeparator() ) ;
		}
		catch( Exception theException )
		{
			log.warning( "Unable to open data file " + getMatchingDataFilename() + ": " + theException.toString() ) ;
			return ;
		}
	}

	private static long parseTimecode( final String timecode )
	{
		final String[] parts = timecode.split( ":" ) ;
		final int hours = Integer.parseInt( parts[ 0 ] ) ;
		final int minutes = Integer.parseInt( parts[ 1 ] ) ;
		final String[] secondsAndMillis = parts[ 2 ].split( "," ) ;
		final int seconds = Integer.parseInt( secondsAndMillis[ 0 ] ) ;
		final int milliseconds = Integer.parseInt( secondsAndMillis[ 1 ] ) ;
	
		return (long) hours * 3600 * 1000 + 
				(long) minutes * 60 * 1000 + 
				(long) seconds * 1000 + 
				milliseconds ;
	}

	/**
	 * Walk through each season and:
	 *  1) Look for missing downloaded subtitle files and queue any missing for later download and add to subtitleDataToDownload structure
	 *  2) Find mkv files without corresponding wav files and add those to the avFiles structure
	 * @param seasonDirectories
	 * @param subtitleDataToDownload
	 * @param avFiles
	 */
	public void setupPreprocessingWork( final List< File > seasonDirectories,
			final List< RenameEpisodesBySRT_DownloadDataClass > subtitleDataToDownload,
			Map< File, TheTVDB_seriesEpisodesClass > seasonDirectoryEpisodes )
	{
		for( File seasonDirectory : seasonDirectories )
		{
			log.info( "Processing show season " + seasonDirectory.getAbsolutePath() + "..." ) ;

			final String tvdbShowIDString = FileNamePattern.getTVDBShowID( seasonDirectory ) ;
			final String imdbShowIDString = FileNamePattern.getIMDBShowID( seasonDirectory ) ;

			final int seasonNumber = FileNamePattern.getShowSeasonNumber( seasonDirectory ) ;
			final TheTVDB_seriesEpisodesClass seasonEpisodes = theTVDB.getSeriesEpisodesInfo( tvdbShowIDString, Integer.toString( seasonNumber ) ) ;
			seasonDirectoryEpisodes.put( seasonDirectory, seasonEpisodes ) ;

			// Compare the total number of episodes for this season with the srt files already downloaded.
			final Set< Integer > missingDownloadedSRTFiles = findMissingDownloadedSRTEpisodes( imdbShowIDString, seasonDirectory, seasonEpisodes ) ;

			List< OpenSubtitles_Data > allSubtitlesForSeason = new ArrayList< OpenSubtitles_Data >() ;

			// Only get subtitle information for the episodes that are missing.
			for( Integer episodeNumberInteger : missingDownloadedSRTFiles )
			{
				OpenSubtitles_SubtitlesResponse response = openSubtitles.searchForSubtitlesByIMDBID(
						imdbShowIDString,
						String.valueOf( seasonNumber ),
						episodeNumberInteger.toString(),
						1 ) ;
				if( null == response )
				{
					log.warning( "null response when searching for subtitles by id " + imdbShowIDString + ", season " + seasonNumber + " episode " + episodeNumberInteger ) ;
					continue ;
				}
				allSubtitlesForSeason.addAll( response.getData() ) ;
			}

			log.info( "Finding best subtitles..." ) ;

			// Find the best subtitle file for each episode missing a downloaded subtitle file.
			final List< OpenSubtitles_Data > bestSubtitleForEachEpisode = openSubtitles.findBestSubtitleFileIDsToDownloadForSeason( allSubtitlesForSeason ) ;
			for( OpenSubtitles_Data theData : bestSubtitleForEachEpisode )
			{
				final int episodeNumber = theData.getAttributes().getFeature_details().getEpisode_number().intValue() ;
				RenameEpisodesBySRT_DownloadDataClass downloadData = new RenameEpisodesBySRT_DownloadDataClass(
						imdbShowIDString, seasonNumber, episodeNumber, theData, seasonDirectory ) ;
				subtitleDataToDownload.add( downloadData ) ;
			}
		} // for( seasonDirectory )
	}

	public boolean shouldKeepRunning()
	{
		return !common.shouldStopExecution( getStopFileName() ) ;
	}

	/**
	 * For each generatdSRTFile, match against the downloadedSRTFile and add the generatedSRTFile and its match score to a Map and return it.
	 * Based on some testing, use Cosine Distance with 4 minute wav/srt.
	 * @param downloadedSRTFile
	 * @param generatedSRTFiles
	 * @return
	 */
	public TreeMap< Double, File > test_matchDownloadedSRTFilesWithGeneratedSRTFiles( final RenameEpisdodesBySRT_SRTData downloadedSRTFileData,
			final List< RenameEpisdodesBySRT_SRTData > generatedSRTFilesData,
			BufferedWriter dataFileWriter )
	{
		assert( downloadedSRTFileData != null ) ;
		assert( generatedSRTFilesData != null ) ;

		TreeMap< Double, File > matchingScores = new TreeMap< Double, File >() ;
		FuzzyScore fuzzyScore = new FuzzyScore( Locale.ENGLISH ) ; // Higher is better
		LevenshteinDistance levenshsteinDistance = LevenshteinDistance.getDefaultInstance() ; // Lower is better
		LongestCommonSubsequenceDistance longestCommonSubsequenceDistance = new LongestCommonSubsequenceDistance() ; // Lower is better
		CosineDistance cosineDistance = new CosineDistance() ; // Lower is better
		//		CosineSimilarity cosineSimilarity = new CosineSimilarity() ;
		JaroWinklerDistance jaroWinklerDistance = new JaroWinklerDistance() ; // Lower is better

		final String firstMinutesOfDownloadedSRTFile = downloadedSRTFileData.getFirstMinutesOfSubtitleText() ;

		for( RenameEpisdodesBySRT_SRTData generatedSRTFileData : generatedSRTFilesData )
		{
			final String firstMinutesOfGeneratedSRTFile = generatedSRTFileData.getFirstMinutesOfSubtitleText() ;
			final Integer fuzzyScoreInteger = fuzzyScore.fuzzyScore( firstMinutesOfDownloadedSRTFile, firstMinutesOfGeneratedSRTFile ) ;
			final Integer levenshsteinDistanceInteger = levenshsteinDistance.apply( firstMinutesOfDownloadedSRTFile, firstMinutesOfGeneratedSRTFile ) ;
			final Integer longestCommonSubsequenceDistanceInstance = longestCommonSubsequenceDistance.apply( firstMinutesOfDownloadedSRTFile, firstMinutesOfGeneratedSRTFile ) ;
			final Double cosineDistanceDouble = cosineDistance.apply( firstMinutesOfDownloadedSRTFile, firstMinutesOfGeneratedSRTFile ) ;
			//			final Double cosineSimilarityDouble = cosineSimilarity.cosineSimilarity( firstMinutesOfDownloadedSRTFile, firstMinutesOfGeneratedSRTFile ) ;
			final Double jaroWinklerDistanceDouble = jaroWinklerDistance.apply( firstMinutesOfDownloadedSRTFile, firstMinutesOfGeneratedSRTFile ) ;

			final int score = levenshsteinDistanceInteger.intValue() + longestCommonSubsequenceDistanceInstance.intValue() ;
			// Based on some initial testing, the cosine distance performs the best for this data set (tv show "24")
			matchingScores.put( cosineDistanceDouble,  generatedSRTFileData.getSrtFile() ) ;

			// dataFileWriter format:
			// downloadedsrtfilename,generatedsrtfilename,fuzzy score,levenshtein,longestcommonsubsequence,cosine distance,jarowinkler distance,levensthein+longestcommon
			try
			{
				dataFileWriter.write( downloadedSRTFileData.getSrtFile().getName()
						+ "," + generatedSRTFileData.getSrtFile().getName()
						+ "," + fuzzyScoreInteger
						+ "," + levenshsteinDistanceInteger
						+ "," + longestCommonSubsequenceDistanceInstance
						+ "," + cosineDistanceDouble
						+ "," + jaroWinklerDistanceDouble
						+ "," + score
						+ System.lineSeparator() ) ;
			}
			catch( Exception theException )
			{
				log.warning( "Write error to data file " + getMatchingDataFilename() + ": " + theException.toString() ) ;
			}
		}

		// Code used to test the matching algorithms from execute()
		//				for( RenameEpisdodesBySRT_SRTData downloadedSRTFileData : downloadedSRTFilesData )
		//				{
		//					TreeMap< Double, File > downloadedSRTFileScores = test_matchDownloadedSRTFilesWithGeneratedSRTFiles( downloadedSRTFileData, generatedSRTFilesData, dataFileWriter ) ;
		//					downloadedSRTScoringMap.put( downloadedSRTFileData.getSrtFile(), downloadedSRTFileScores ) ;
		//				}
		//				
		//				for( Map.Entry< File, TreeMap< Double, File > > entry : downloadedSRTScoringMap.entrySet() )
		//				{
		//					final File downloadedSRTFile = entry.getKey() ;
		//					
		//					// scoringMap is a TreeMap so guaranteed to be ordered.
		//					final TreeMap< Double, File > scoringMap = entry.getValue() ;
		//					
		//					final Double lowestScoreDouble = scoringMap.firstEntry().getKey() ;
		//					final File lowestScoreFile = scoringMap.firstEntry().getValue() ;
		//					
		//					log.info( "Best match for " + downloadedSRTFile.getAbsolutePath() + " is [" + lowestScoreDouble + "," + lowestScoreFile.getAbsolutePath() + "]" ) ;					
		//				}
		
		return matchingScores ;
	}
}
