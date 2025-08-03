package run_ffmpeg;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.mongodb.client.MongoCollection;

import run_ffmpeg.ffmpeg.FFmpeg_ProbeResult;
import run_ffmpeg.ffmpeg.FFmpeg_Stream;

public class CheckSubtitleTypes
{
	/// Setup the logging subsystem
	protected transient Logger log = null ;
	protected transient Common common = null ;

	protected MoviesAndShowsMongoDB masMDB = null ; 

	/// Handle to the database. This instance is NOT thread safe.
	protected transient MongoCollection< FFmpeg_ProbeResult > probeInfoCollection = null ;

	/// This map will store all of the FFmpegProbeResults in the probeInfoCollection, keyed by the long path to the document.
	/// This instance is thread safe.
	protected transient Map< String, FFmpeg_ProbeResult > probeInfoMap = new HashMap< String, FFmpeg_ProbeResult >() ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_check_subtitle_types.txt" ;

	public CheckSubtitleTypes()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;

		initObject() ;
	}

	public CheckSubtitleTypes( Logger log, Common common )
	{
		this.log = log ;
		this.common = common ;

		initObject() ;
	}

	private void initObject()
	{
		masMDB = new MoviesAndShowsMongoDB() ;
		probeInfoCollection = masMDB.getProbeInfoCollection() ;
		
		log.info( "Loading probe info collection" ) ;
		probeInfoMap = masMDB.loadProbeInfoMap() ;
		log.info( "Loaded " + probeInfoMap.size() + " item(s)" ) ;
	}

	public static void main( String[] args )
	{
		(new CheckSubtitleTypes()).execute() ;
	}

	public void execute()
	{
		log.info( "Looking for unique codec names..." ) ;
		
		// probeInfoMap should already be populated.
		// Map< codec_name, FFmpegProbeResult >
		Map< String, FFmpeg_ProbeResult > codecNames = new HashMap< String, FFmpeg_ProbeResult >() ;
		for( Map.Entry< String, FFmpeg_ProbeResult > entry : probeInfoMap.entrySet() )
		{
			final FFmpeg_ProbeResult theResult = entry.getValue() ;
			final List< FFmpeg_Stream > subtitleStreams = theResult.getStreamsByCodecType( "subtitle" ) ;

			for( FFmpeg_Stream theStream : subtitleStreams )
			{
				final String codecName = theStream.getCodec_name() ;
				if( !codecNames.containsKey( codecName  ) )
				{
					// First time seeing this codec name
					// Add it
					codecNames.put( codecName, theResult ) ;
				}
			} // for( theStream )
		} // for( entry )
		
		log.info( "Found " + codecNames.size() + " unique code name(s): " ) ;
		for( Map.Entry< String, FFmpeg_ProbeResult > entry : codecNames.entrySet() )
		{
			log.info( "Name: " + entry.getKey() + " in " + entry.getValue().getFileNameWithPath() ) ;
		}
		log.info( "Done." ) ;
	} // execute()
}
