package run_ffmpeg;

import java.util.ArrayList;
import java.util.Collections;
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

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.WordUtils;
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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

/**
 * This class matches and renames .mkv files for tv shows to their proper series/season/episode names. It also updates the names
 *  of the .srt files.
 * Applies only to TV shows, not movies, but will skip movies.
 * It downloads reference srt files from opensubtitles and compares those against the srt files for the .mkv files.
 * Downloading reference srt files is throttled by opensubtitles, but this application includes wait operations to eventually
 *  download them all.
 * Precondition: The .mkv files MUST already have one srt file for each (use Subtitles_LoadDatabase and WorkflowOrchestrator).
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
	protected TheTVDB theTVDB = null ;
	protected SRTFileUtils srtFileUtils = null ;

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

		log.info( "Logging into tvdb" ) ;
		theTVDB = new TheTVDB( log, common ) ;
		srtFileUtils = new SRTFileUtils( log, common ) ;

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
		// -> Download SRT files whenever possible, and work the other actions otherwise.
		// Generalize this to queue all work ahead of time and then do one unit of work each iteration while checking for
		//  download time expiration.
		List< RenameEpisodesBySRT_DownloadDataClass > subtitleDataToDownload = new ArrayList< RenameEpisodesBySRT_DownloadDataClass >() ;

		// Can add shows or seasons individually
		// showDirectories is the set of tv show directories, one directory for each show
		List< File > showDirectories = new ArrayList< File >() ;
		showDirectories.addAll( getShowDirectories( Common.getPathToOCR() ) ) ;
		//showDirectories.add( new File( "\\\\skywalker\\Media\\To_OCR\\Arrested Development (2003) {imdb-0367279} {tvdb-72173}" ) ) ;
		//showDirectories.add( new File( "\\\\skywalker\\Media\\To_OCR\\Greys Anatomy (2005) {imdb-0413573} {tvdb-73762}" ) ) ;

		// seasonDirectories will be populated with each season of each show listed in the showDirectories structure.
		// Can also add individual seasons directly into the list.
		List< File > seasonDirectories = new ArrayList< File >() ;
		seasonDirectories.addAll( getSeasonDirectoriesFromShowDirectories( showDirectories ) ) ;
		//		seasonDirectories.add( new File( "\\\\skywalker\\Media\\To_OCR\\Arrested Development (2003) {imdb-0367279} {tvdb-72173}\\Season 02" ) ) ;
		//		seasonDirectories.add( new File( "\\\\skywalker\\Media\\To_OCR\\Greys Anatomy (2005) {imdb-0413573} {tvdb-73762}\\Season 01" ) ) ;

		// This structure stores tvdb information about each season in the seasonDirectories structure, indexed by the directory File.
		Map< File, TheTVDB_seriesEpisodesClass > seasonDirectoryEpisodes = new HashMap< File, TheTVDB_seriesEpisodesClass >() ;

		// Walk through the seasonDirectories and identify which subtitles to download
		queueSRTFilesToDownload( seasonDirectories, subtitleDataToDownload, seasonDirectoryEpisodes ) ;

		// Download the subtitle files.
		// This is a blocking call that will accommodate for the throttling, and subsequent delays, of downloading srt
		//  files from open subtitles.
		downloadSRTFilesWithThrottling( subtitleDataToDownload ) ;

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

	/**
	 * Return the mkv file name corresponding to the given srt file.
	 * The srt file could be of the form:
	 *  "Name.srt"
	 *  "Name.en.srt"
	 *  "Name.en.1.srt" where 1 could be any number (stream number).
	 * @param srtFile
	 * @return
	 */
	public String buildMKVBaseNameFromSRTFile( final File srtFile )
	{
		assert( srtFile != null ) ;

		// Pattern includes baseName, optional ".en" and optional ".1" (any stream, 1 for example) and ".srt"
		final Pattern srtFilePattern = Pattern.compile( "(?<baseName>.*?)(\\.en)?(\\.[\\d]+)?\\.srt" ) ;
		final Matcher srtFileMatcher = srtFilePattern.matcher( srtFile.getName() ) ;

		String baseName = null ;
		if( srtFileMatcher.find() )
		{
			baseName = srtFileMatcher.group( "baseName" ) ;
		}
		return baseName ;
	}

	/**
	 * Find the best match for each downloaded srt file and rename the corresponding mkv and its generated srt files appropriately.
	 * Matching is performed by examining the first n minutes of each downloaded srt file with each generated srt file. From there,
	 * the corresponding mkv file from the generated srt file is located, the proper episode information is built, and the files
	 * are renamed.
	 * The algorithm must match each downloaded srt file to one generated srt file, but not every generated srt file will be matched.
	 *  This is because some generated srt files are for extras and have no corresponding formal episode -- if we were to try to
	 *  match an extra's srt to an episode, we would get the wrong answer and likely overwrite  a better match otherwise.
	 *  This is addressed by pruning which mkv/srt files are examined by looking for the shortest duration episode reported by
	 *  the tvdb and pruning mkvs to only those at least as long in duration as the minimum show duration.
	 * @param seasonDirectories
	 * @param seasonDirectoryEpisodes
	 */
	public void conductMatching( final List< File > seasonDirectories, final Map< File, TheTVDB_seriesEpisodesClass > seasonDirectoryEpisodes )
	{
		assert( seasonDirectories != null ) ;
		assert( seasonDirectoryEpisodes != null ) ;

		for( File seasonDirectory : seasonDirectories )
		{
			conductMatching( seasonDirectory, seasonDirectoryEpisodes.get( seasonDirectory ) ) ;
		}
	}

	public void conductMatching( final File seasonDirectory, final TheTVDB_seriesEpisodesClass seasonEpisodes )
	{
		assert( seasonDirectory != null ) ;
		assert( seasonEpisodes != null ) ;

		final List< File > downloadedSRTFiles = common.getFilesInDirectoryByRegex( seasonDirectory, "[\\d]+ - S[\\d]+E[\\d]+ - downloaded.srt" ) ;
		final List< File > mkvFiles = common.getFilesInDirectoryByRegex( seasonDirectory, "^(?!.*downloaded).*\\.mkv$" ) ;

		// Find the minimum duration, in seconds, of the episodes as measured by thetvdb.
		double minEpisodeDuration = seasonEpisodes.getMinDuration() ;

		// Check if this is a half-hour or hour show.
		// All durations are in seconds.
		if( minEpisodeDuration <= (30 * 60) )
		{
			// Half-hour show.
			// Since thetvdb is notoriously inaccurate (may show 25 minutes for min duration of shows that are actually 20 minutes long), adjust
			// the min duration to account for actual video length.
			minEpisodeDuration = 20 * 60 ;
		}
		else
		{
			// Hour-long show.
			minEpisodeDuration = 40 * 60 ;
		}

		// Remove those mkv files with duration shorter than the min show duration. This will eliminate behind the scenes, interviews, etc., but
		// will likely leave a few mkv files in place that are long (such as compilations of deleted scenes and Making Of types of videos).
		final List< File > mkvFilesPruned = common.pruneByMinDuration( mkvFiles, minEpisodeDuration ) ;
		Collections.sort( mkvFilesPruned ) ;

		// Now that I have the list of mkvFiles that should be episodes, get the srt file for each.
		// Note that this is for the purposes of the comparisons -- it is possible that an mkv file
		//  has multiple srt files, so the renaming code will need to address that.
		// Structure will be Pair< mkvFile, srtFile >.
		List< Pair< File, File > > mkvAndGeneratedSRTFiles = new ArrayList< Pair< File, File > >() ;
		for( File mkvFile : mkvFilesPruned )
		{
			final String srtFileName = mkvFile.getName().replace( ".mkv", ".en.srt" ) ;
			final File srtFile = new File( mkvFile.getParentFile(), srtFileName ) ;
			if( !srtFile.exists() )
			{
				log.warning( "No srt file for mkv file: " + mkvFile ) ;
			}
			else
			{
				mkvAndGeneratedSRTFiles.add( Pair.of( mkvFile, srtFile ) ) ;
			}
		}
		// PC: Now have the mkv files and their associated srt files, and the download srt files for the season episodes.

		// Read the downloaded srt files into memory and normalize them.	
		final List< Pair< File, String > > normalizedDownloadedSRTFiles = srtFileUtils.readAndNormalizeSRTFiles( downloadedSRTFiles ) ;

		// Prepare for renaming operations below.
		final String showName = FileNamePattern.getShowName_static( seasonDirectory ) ;
		final int seasonNumber = FileNamePattern.getShowSeasonNumber( seasonDirectory ) ;

		for( Pair< File, File > mkvSRTPair : mkvAndGeneratedSRTFiles )
		{
			final File mkvFile = mkvSRTPair.getLeft() ;
			final File generatedSRTFile = mkvSRTPair.getRight() ;

			final File bestMatchingDownloadedSRTFile =
					matchGeneratedSRTWithBestDownloadedSRT_withBioInformatics( generatedSRTFile, normalizedDownloadedSRTFiles ) ;

			final int episodeNumber = FileNamePattern.getEpisodeNumber_static( bestMatchingDownloadedSRTFile ) ;
			final String episodeName = getEpisodeName( seasonEpisodes, seasonNumber, episodeNumber ) ;

			// This is a bit ugly to work around a limitatio nof WordUtils.capitalizeFully: It will mix capitalization of even
			//  strings like "S02E03" to "S03e03".
			String episodeBaseName = WordUtils.capitalizeFully( FileNamePattern.stripInvalidSubStrings( showName ), ' ' )  ;
			episodeBaseName += " - S" ;
			if( seasonNumber < 10 ) episodeBaseName += "0" ;
			episodeBaseName += "" + seasonNumber + "E" ;
			if( episodeNumber < 10 ) episodeBaseName += "0" ;
			episodeBaseName += "" + episodeNumber ;
			episodeBaseName += " - " + WordUtils.capitalizeFully( FileNamePattern.stripInvalidSubStrings( episodeName ), ' ' ) ;

			// Rename the mkv file.
			final String newMKVFileName = episodeBaseName + ".mkv" ;
			final File newMKVFile = new File( seasonDirectory, newMKVFileName ) ;
			log.info( "Rename: " + mkvFile.getAbsolutePath() + " -> " + newMKVFile.getAbsolutePath() ) ;
			if( !common.getTestMode() )
			{
				mkvFile.renameTo( newMKVFile ) ;
			}

			// Rename the srt files associated with the original mkv file.
			final List< File > srtFilesMatchingInputMKVFile = common.getFilesInDirectoryByRegex(
					seasonDirectory, mkvFile.getName().replace( ".mkv", ".*\\.srt" ) ) ;
			renameSRTFilesWithNewBasename( srtFilesMatchingInputMKVFile, episodeBaseName ) ;
		}
	} // performMatching()

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

	/**
	 * This method is designed to download all srts in the given structure.
	 * This method will slowly spin to overcome any restrictions to download created by opensubtitles (20 downloads per four hours).
	 * @param subtitleDataToDownload The subtitle files to download.
	 */
	public void downloadSRTFilesWithThrottling( List< RenameEpisodesBySRT_DownloadDataClass > subtitleDataToDownload )
	{
		// Main loop for processing data items
		// Will only conduct one unit of work per loop to minimize time between subtitle file downloads
		boolean didWorkThisLoop = false ;
		while( shouldKeepRunning() && !subtitleDataToDownload.isEmpty() )
		{
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

				// Skip the rest of the loop so the next iteration can start on downloading subtitles.
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

	public Set< Integer > findMissingDownloadedSRTEpisodes( final String imdbShowIDString, final File seasonDirectory,
			final TheTVDB_seriesEpisodesClass seasonEpisodes )
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

	/**
	 * Return the best matching downloaded srt file to input generatedSRTFile.
	 * @param generatedSRTFile
	 * @param normalizedDownloadedSRTFiles
	 * @return
	 */
	public File matchGeneratedSRTWithBestDownloadedSRT_withBioInformatics( final File generatedSRTFile,
			final List< Pair< File, String > > normalizedDownloadedSRTFiles )
	{
		assert( generatedSRTFile != null ) ;
		assert( generatedSRTFile.exists() ) ;
		assert( normalizedDownloadedSRTFiles != null ) ;

		final String normalizedGeneratedSRTFileData = srtFileUtils.readAndNormalizeSRTFile( generatedSRTFile ) ;
		Pair< Double, File > bestDownloadedMatch = Pair.of( Double.MAX_VALUE, null ) ;

		for( Pair< File, String > normalizedDownloadedSRTFilePair : normalizedDownloadedSRTFiles )
		{
			final File normalizedDownloadedSRTFile = normalizedDownloadedSRTFilePair.getLeft() ;
			final String normalizedDownloadedSRTFileData = normalizedDownloadedSRTFilePair.getRight() ;

			// Compare this downloadedSRTFile with the generated SRT file
			CosineDistance cosineDistance = new CosineDistance() ; // Lower is better
			final Double cosineDistanceDouble = cosineDistance.apply( normalizedGeneratedSRTFileData, normalizedDownloadedSRTFileData ) ;

			if( (null == bestDownloadedMatch.getRight()) || (cosineDistanceDouble.doubleValue() < bestDownloadedMatch.getLeft()) )
			{
				// No matches yet or this is a better match.
				bestDownloadedMatch = Pair.of( cosineDistanceDouble, normalizedDownloadedSRTFile ) ;
			}
		} // for( downloadedSRTFile )
		log.info( "Best match for " + generatedSRTFile.getName() + " is " + bestDownloadedMatch.getRight().getName() ) ;

		return bestDownloadedMatch.getRight() ;
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

	/**
	 * For each season in seasonDirectories:
	 * - Download information about episodes from that season from tvdb and add to the seasonDirectoryEpisodes sorted by seasonDirectory.
	 * Find which srt files from opensubtitles are missing and choose the best option for each episode and store those in the subtitleDataToDownload structure.
	 * @param seasonDirectories Directories, one for each season, to choose subtitles to download.
	 * @param subtitleDataToDownload The srt from opensubtitles to download for each episode missing a downloaded srt.
	 * @param seasonDirectoryEpisodes Information about episodes, from tvdb, for each season, sorted in the Map by seasonDirectory.
	 */
	public void queueSRTFilesToDownload( final List< File > seasonDirectories,
			List< RenameEpisodesBySRT_DownloadDataClass > subtitleDataToDownload,
			Map< File, TheTVDB_seriesEpisodesClass > seasonDirectoryEpisodes )
	{
		for( File seasonDirectory : seasonDirectories )
		{
			log.info( "Processing show season " + seasonDirectory.getAbsolutePath() + "..." ) ;

			// Get information about the show and season
			final String tvdbShowIDString = FileNamePattern.getTVDBShowID( seasonDirectory ) ;
			final String imdbShowIDString = FileNamePattern.getIMDBShowID( seasonDirectory ) ;

			final int seasonNumber = FileNamePattern.getShowSeasonNumber( seasonDirectory ) ;

			// Download information about episodes from this season
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

	public void renameSRTFilesWithNewBasename( final List< File > srtFiles, final String baseName )
	{
		final Pattern extensionsPattern = Pattern.compile( "(?<showName>.*?)(?<allButBaseName>(\\.en)?(\\.[\\d]+)?\\.srt)" ) ;
		for( File srtFile : srtFiles )
		{
			final String fileName = srtFile.getName() ;
			final Matcher extensionsMatcher = extensionsPattern.matcher( fileName ) ;
			if( !extensionsMatcher.find() )
			{
				log.warning( "No match for extensionsMatcher against fileName: " + fileName ) ;
				continue ;
			}
			final String allButBaseName = extensionsMatcher.group( "allButBaseName" ) ;
			// allButBaseName includes the preceding '.'
			final String srtOutputFileName = baseName + allButBaseName ;
			final File srtOutputFile = new File( srtFile.getParentFile(), srtOutputFileName ) ;

			log.info( "Rename: " + srtFile.getAbsolutePath() + " -> " + srtOutputFile.getAbsolutePath() ) ;
			if( !common.getTestMode() )
			{
				srtFile.renameTo( srtOutputFile ) ;
			}
		}
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
