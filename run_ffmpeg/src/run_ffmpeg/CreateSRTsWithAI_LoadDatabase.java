package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;

import com.mongodb.client.MongoCollection;

import run_ffmpeg.ffmpeg.FFmpeg_ProbeResult;
import run_ffmpeg.ffmpeg.FFmpeg_Stream;

/**
 * This class identifies media files with subtitles that cannot be extracted with ExtractAndOCRSubtitles (non-PGS subtitles)
 * and adds them to the action_createSRTsWithAI collection in the mongodb.
 */
public class CreateSRTsWithAI_LoadDatabase
{
	/// Setup the logging subsystem
	private Logger log = null ;

	/// The set of methods and variables for common use.
	private Common common = null ;

	private MoviesAndShowsMongoDB masMDB = null ;
	protected Map< String, File[] > filesInDirectoriesCache = new HashMap< String, File[] >() ;
	protected int cacheHits = 0 ;
	protected int cacheMisses = 0 ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_create_srts_with_ai_load_database.txt" ;

	public CreateSRTsWithAI_LoadDatabase()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;
		masMDB = new MoviesAndShowsMongoDB( log ) ;
	}

	public static void main( String[] args )
	{
		(new CreateSRTsWithAI_LoadDatabase()).execute() ;
	}

	public void execute()
	{
		common.setTestMode( true ) ;

		log.info( "Loading probe info collection" ) ;
		final List< FFmpeg_ProbeResult > allProbeInfoInstances = masMDB.getAllProbeInfoInstances() ;
		log.info( "Loaded " + allProbeInfoInstances.size() + " probe info instance(s)" ) ;

		List< FFmpeg_ProbeResult > filesToGenerateSRT = new ArrayList< FFmpeg_ProbeResult >() ;
		for( FFmpeg_ProbeResult theProbeResult : allProbeInfoInstances )
		{
//			log.fine( "Processing theProbeResult: " + theProbeResult.toString() ) ;

			// Get each subtitle stream.
			final List< FFmpeg_Stream > subtitleStreams = theProbeResult.getStreamsByCodecType( "subtitle" ) ;
			// subtitleStreams guaranteed to be non-null but may be empty

			for( FFmpeg_Stream theStream : subtitleStreams )
			{
//				log.fine( "Processing theStream: " + theStream.toString() ) ;
				
				// I only care about English subtitles
				if( null == theStream.tags )
				{
					log.warning( "null theStream.tags for stream " + theStream.index + " of file " + theProbeResult.getFileNameWithPath() ) ;
					continue ;
				}
				
				final String theLanguage = theStream.tags.get( "language" ) ;
				if( null == theLanguage )
				{
					// This is ok for some types of subtitles...I think, but probably not worth proceeding with here.
					log.fine( "null theLanguage for stream " + theStream.index + " of file " + theProbeResult.getFileNameWithPath() ) ;
					continue ;
				}

				if( !theLanguage.equals( "eng" ) )
				{
					// non-English subtitle stream
					log.fine( "Skipping language " + theStream.tags.get( "language" )
						+ " for stream " + theStream
						+ " in file " + theProbeResult.getFileNameWithPath() ) ;
					continue ;
				}

				if( canBeExtractedWithFFmpeg( theStream ) )
				{
					// This method is only for using whisper; skip anything that can be extracted with ffmpeg
					continue ;
				}

				if( subtitleFileAlreadyExists( theProbeResult ) )
				{
					continue ;
				}

				filesToGenerateSRT.add( theProbeResult ) ;
				
				// Only need to add the probe result once per file -- multiple streams mean nothing for this algorithm
				break ;
			}
		}
		log.info( "Added " + filesToGenerateSRT.size() + " file(s) to convert" ) ;

//		for( FFmpeg_ProbeResult theProbeResult : filesToGenerateSRT )
//		{
//			log.fine( "Writing to database: _id " + theProbeResult._id.toString() + " for " + theProbeResult.getFileNameShort() ) ;
//		}
		
		if( !filesToGenerateSRT.isEmpty() )
		{
			log.info( "Clearing old actions..."  ) ;
			masMDB.dropAction_CreateSRTsWithAICollection() ;

			log.info( "Adding " + filesToGenerateSRT.size() + " file(s) to database..." ) ;
			MongoCollection< FFmpeg_ProbeResult > createSRTsHandle = masMDB.getAction_CreateSRTsWithAICollection() ;
			createSRTsHandle.insertMany( filesToGenerateSRT ) ;
		}
		log.info( "Cache performance> Hits: " + cacheHits + ", Misses: " + cacheMisses ) ;
		log.info( "Shutdown." ) ;
	}

	/**
	 * Return true if at least one srt file already exists for this probe file, false otherwise.
	 * @param theProbeResult
	 * @return
	 */
	public boolean subtitleFileAlreadyExists( final FFmpeg_ProbeResult theProbeResult )
	{
		assert( theProbeResult != null ) ;

		// Match the given filename with an srt equivalent:
		// 21 Jump Street (2012).mkv
		// -> 21 Jump Street (2012).en.srt
		// -> 21 Jump Street (2012).en.7.srt

		// Use regex to perform the matching
		// Convert to: 21 Jump Street (2012)\..*\.srt
		final String filenameWithRegex = Pattern.quote( FilenameUtils.getBaseName( theProbeResult.getFileNameShort() ) )
				+ "\\." // add the period
				+ ".*" // add the wildcard matcher
				+ "\\." // add the period before the extension
				+ "srt" ; // add the extension
//		final String regexFileNameWithPath = FilenameUtils.getFullPath( theProbeResult.getFileNameWithPath() ) + filenameWithRegex ;
		final Pattern fileMatchPattern = Pattern.compile( filenameWithRegex ) ;

		// Get the files in the home directory for this media file
		final File[] filesInDirectory = getFilesInDirectoryWithCaching( FilenameUtils.getFullPath( theProbeResult.getFileNameWithPath() ) ) ;
		
		// Conduct a regex matching
		for( File theFile : filesInDirectory )
		{
			final Matcher fileMatcher = fileMatchPattern.matcher( theFile.getName() ) ;
			if( fileMatcher.find() )
			{
				// Found a match
				return true ;
			}
		}
		return false ;
	}

	public File[] getFilesInDirectoryWithCaching( final String fullPath )
	{
		assert( fullPath != null ) ;
		
		File[] filesInDirectory = filesInDirectoriesCache.get( fullPath ) ;
		if( null == filesInDirectory )
		{
			// Cache miss
			++cacheMisses ;
			final File directoryFile = new File( fullPath ) ;
			filesInDirectory = directoryFile.listFiles() ;
			filesInDirectoriesCache.put( fullPath, filesInDirectory ) ;
		}
		else
		{
			++cacheHits ;
		}
		return filesInDirectory ;
	}
	
	public boolean canBeExtractedWithFFmpeg( final FFmpeg_Stream theStream )
	{
		assert( theStream != null ) ;

		if( theStream.codec_name.equals( "hdmv_pgs_subtitle" ) || theStream.codec_name.equals( "mov_text" ) )
		{
			return true ;
		}

		return false ;
	}
}
