package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bson.conversions.Bson;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

public class RenameSubtitleFiles
{
	/// Setup the logging subsystem
	private Logger log = null ;

	/// The set of methods and variables for common use.
	private Common common = null ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_rename_subtitle_files.txt" ;

	public RenameSubtitleFiles()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;
	}

	public static void main( String[] args )
	{
		(new RenameSubtitleFiles()).execute() ;
	}

	public void execute()
	{
		common.setTestMode( false ) ;

		execute( Common.getPathToMovies() ) ;
		execute( Common.getPathToTVShows() ) ;
//		execute( "\\\\skywalker\\Media\\TV_Shows\\The Tudors (2007)\\Season 01" ) ;
//		execute( "\\\\skywalker\\Media\\Movies\\Zoolander (2001)" ) ;
//		execute( "\\\\skywalker\\Media\\Movies\\2 Fast 2 Furious (2003) {edition-4K}" ) ;
	}

	public void execute( final String directory )
	{
		// NOTE: Depends on probeInfoCollection being up to date
		common.setTestMode( true ) ;

		// First get all srt files
		List< File > srtFiles = common.getFilesInDirectoryByExtension( directory, "srt" ) ;

		// Next, walk through all video files and check for matching srt files
		MoviesAndShowsMongoDB masMDB =  new MoviesAndShowsMongoDB( log ) ;
		MongoCollection< FFmpegProbeResult > probeInfoCollection = masMDB.getProbeInfoCollection() ;

		log.info( "Loading probe info collection" ) ;
		//Bson findFilesFilter = Filters.regex( "fileNameWithPath", "Zoolander.*2001.*" ) ;
		Bson findFilesFilter = Filters.regex( "fileNameWithPath", ".*" ) ;
		FindIterable< FFmpegProbeResult > probeInfoFindResult = probeInfoCollection.find( findFilesFilter ) ;

		final Pattern properlyFormedSrtFilePattern = Pattern.compile( ".*\\.en\\.srt" ) ;
		final Pattern properlyFormedForcedSubtitleSrtFilePattern = Pattern.compile( ".*\\.en\\.forced\\.srt" ) ;

		// Iterate over each of the video files
		Iterator< FFmpegProbeResult > probeInfoFindResultIterator = probeInfoFindResult.iterator() ;
		while( probeInfoFindResultIterator.hasNext() )
		{
			// Get the next video file
			FFmpegProbeResult probeResult = probeInfoFindResultIterator.next() ;
			// The quote method, or some part of the matching system, messes up with the \E string, such as Movie Name (2000)\Extended Scenes.{en.5.srt,.mkv}
			// Replace the \E with nothing here and hope nothing bad happens
			final String videoPathWithoutExtension = Common.stripExtensionFromFileName( probeResult.getFileNameWithPath() ).replace( "\\E", "" ) ;
			final String videoPathWithoutExtensionQuoted = Pattern.quote( videoPathWithoutExtension ) ;
			final String videoPathWithoutExtensionQuotedPatternString = videoPathWithoutExtensionQuoted + ".*\\.srt" ;
			final Pattern videoFileWithSrtPattern = Pattern.compile( videoPathWithoutExtensionQuotedPatternString ) ;

			// Now check for matching srt files that match the old style format "Name.en.9.srt"
			// If the video file has a corresponding properly formed srt file then skip this video file
			// Properly formed: "Name.en.srt" or "Name.en.forced.srt"
			// Store by filename with path
			List< File > badlyFormedSrtFiles = new ArrayList< File >() ;
			boolean foundProperlyFormedSrtFile = false ;
			for( File srtFile : srtFiles )
			{
				// First, check if this srtFile corresponds to the video file
				final String srtFileWithPathQuotedString = Pattern.quote( srtFile.getAbsolutePath().replace( "\\E", "" ) ) ;
				final Matcher videoFileWithSrtMatcher = videoFileWithSrtPattern.matcher( srtFileWithPathQuotedString ) ;
				boolean foundMatchingVideoAndSrtFiles = videoFileWithSrtMatcher.find() ;
				if( !foundMatchingVideoAndSrtFiles )
				{
					// The video file and srt files do NOT match each other
					// Skip it
					continue ;
				}

				final Matcher properlyFormedSrtFileMatcher = properlyFormedSrtFilePattern.matcher( srtFile.getAbsolutePath() ) ;
				foundProperlyFormedSrtFile = properlyFormedSrtFileMatcher.find() ;
				if( foundProperlyFormedSrtFile )
				{
					break ;
				}
				final Matcher properlyFormedForcedSubtitledSrtFileMatcher = properlyFormedForcedSubtitleSrtFilePattern.matcher( srtFile.getAbsolutePath() ) ;
				foundProperlyFormedSrtFile = properlyFormedForcedSubtitledSrtFileMatcher.find() ;
				if( foundProperlyFormedSrtFile )
				{
					break ;
				}
				// Found a poorly formed srt file with
				badlyFormedSrtFiles.add( srtFile ) ;
			} // for( srtFile )

			if( foundProperlyFormedSrtFile )
			{
				// Found a properly formed srt file, which means this video file has already been processed
				continue ;
			}
			// Post-condition: Need to fix the srt file naming
			// Just assume the lowest numbered srt stream is the correct one *shrug*
			File lowestNumberedSrtFile = findLowestNumberedSrtFile( badlyFormedSrtFiles ) ;
			if( null == lowestNumberedSrtFile )
			{
				// Not an error -- many mkv/mp4 files have no srt file
				log.fine( "lowestNumberedSrtFile is null for video file " + probeResult.getFileNameWithPath() ) ;
			}
			else
			{
//				log.info( "Found lowestNumberedSrtFile: " + lowestNumberedSrtFile ) ;
				final String newFileNameWithPath = lowestNumberedSrtFile.getAbsolutePath().replaceAll( "(.*)\\.([\\d]+)\\.(srt)", "$1\\.$3" ) ;
				final File newFile = new File( newFileNameWithPath ) ;
				log.info( "Rename " + lowestNumberedSrtFile.getAbsolutePath() + " to " + newFile.getAbsolutePath() ) ;

				// Rename the file
				try
				{
					if( !common.getTestMode() )
					{
						lowestNumberedSrtFile.renameTo( newFile ) ;
					}
				}
				catch( Exception theExteption )
				{
					log.warning( "Exception remaining source file from " + lowestNumberedSrtFile.getAbsolutePath() + " to " + newFile.getAbsolutePath() ) ;
				}
			} // else ( lowestNumberedSrtFile )
		} // while( probeInfoFindResultIterator.hasNext() )
	} // execute( String )

	public File findLowestNumberedSrtFile( final List< File > srtFiles )
	{
		if( 1 == srtFiles.size() )
		{
			// When we have only a single choice, the choice is clear
			return srtFiles.get( 0 ) ;
		}

		if( srtFiles.isEmpty() )
		{
			// Lots of movies/shows will have no corresponding srt files (DVDs for example)
			log.fine( "Empty srtFiles" ) ;
			return null ;
		}

		// Set the first srt as the lowest numbered srt stream
		File lowestNumberedSrtStream = srtFiles.get( 0 ) ;
		
		// Check that it matches with a poorly formed file name
		final Pattern streamNumberPattern = Pattern.compile( ".*\\.en\\.(\\d+)\\.srt" ) ;
		Matcher streamNumberMatcher = streamNumberPattern.matcher( lowestNumberedSrtStream.getAbsolutePath() ) ;

		boolean isPatternMatch = streamNumberMatcher.find() ;
		if( !isPatternMatch )
		{
			// Not sure this is really what I want -- this neglects the remaining srt files, if any, and only returns
			// a failure based on the first.
			// Relying here on the warning to pop out in the log to fix the file name first -- this shouldn't happen often
			log.warning( "No pattern match for first check on file: " + lowestNumberedSrtStream.getAbsolutePath() ) ;
			return null ;
		}
		// Post-condition: at least one poorly formed srt file exists in the list of srtFiles
		
		// Extract the stream number and assign it as the lowest number so far recorded
		String streamNumberString = streamNumberMatcher.group( 1 ) ;
		Integer streamNumberInteger = Integer.parseInt( streamNumberString ) ;
		int lowestStreamNumber = streamNumberInteger.intValue() ;

		// Iterate through the srt files looking for a stream that has lower number than the current lowest
		for( File srtFile : srtFiles )
		{
			if( srtFile == lowestNumberedSrtStream )
			{
				// First check, skip it
				continue ;
			}
			// Post-condition: srtfile is NOT the first/lowestNumberedSrtStream file
			streamNumberMatcher = streamNumberPattern.matcher( srtFile.getAbsolutePath() ) ;
			isPatternMatch = streamNumberMatcher.find() ;

			if( !isPatternMatch )
			{
				// This file name doesn't match ".*\\.en\\.(\\d+)\\.srt"
				log.warning( "No pattern match for subsequent check on file: " + srtFile.getAbsolutePath() ) ;
				continue ;
			}
			streamNumberString = streamNumberMatcher.group( 1 ) ;
			streamNumberInteger = Integer.parseInt( streamNumberString ) ;
			int localLowestStreamNumber = streamNumberInteger.intValue() ;

			if( localLowestStreamNumber < lowestStreamNumber )
			{
				// Found a new lowest stream number
				lowestNumberedSrtStream = srtFile ;
				lowestStreamNumber = localLowestStreamNumber ;
			}
		} // for( srtfile )
		return lowestNumberedSrtStream ;
	}
}
