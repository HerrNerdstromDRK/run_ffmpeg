package run_ffmpeg;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import com.mongodb.client.MongoCollection;

import run_ffmpeg.ffmpeg.FFmpeg_ProbeResult;

/**
 * This class identifies codecs that will not play on the plex on Roku and adds them to the action_transcodemkvfiles collection in the mongodb.
 */
public class UpgradeMKV_LoadDatabase
{
	/// Setup the logging subsystem
	private Logger log = null ;

	/// The set of methods and variables for common use.
	private Common common = null ;

	private MoviesAndShowsMongoDB masMDB = null ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_upgrade_mkv_load_database.txt" ;

	public UpgradeMKV_LoadDatabase()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;
		masMDB = new MoviesAndShowsMongoDB( log ) ;
	}

	public static void main( String[] args )
	{
		(new UpgradeMKV_LoadDatabase()).execute() ;
	}

	public void execute()
	{
		common.setTestMode( true ) ;

		log.info( "Loading probe info collection" ) ;
		final List< FFmpeg_ProbeResult > allProbeInfoInstances = masMDB.getAllProbeInfoInstances() ;

		Set< String > videoCodecTypes = listAllVideoCodes( allProbeInfoInstances ) ;
		log.info( "Found video codecs: " + videoCodecTypes.toString() ) ;

		final List< FFmpeg_ProbeResult > filesToUpgrade = findFilesThatNeedUpgrade( allProbeInfoInstances ) ;
		log.info( "Found " + filesToUpgrade.size() + " file(s) to upgrade" ) ;
		
//		for( FFmpegProbeResult theProbeResult : filesToUpgrade )
//		{
//			log.fine( "Upgrade: " + theProbeResult.getFileNameWithPath() ) ;
//		}

		log.info( "Clearing old actions..."  ) ;
		masMDB.dropAction_TranscodeMKVFileInfoCollection() ;
		
		log.info( "Adding files to database..." ) ;
		MongoCollection< FFmpeg_ProbeResult > transcodeDatabaseJobHandle = masMDB.getAction_TranscodeMKVFileInfoCollection() ;
		transcodeDatabaseJobHandle.insertMany( filesToUpgrade ) ;

		log.info( "Shutdown." ) ;
	}

	/**
	 * Return a Set of all video codec names. This is useful to identify which types are in the library so we can
	 *  know which we need to evaluate as things continue to change.
	 */
	public Set< String > listAllVideoCodes( final List< FFmpeg_ProbeResult > allProbeInfoInstances )
	{
		Set< String > videoCodecNames = new HashSet< String >() ;
		
		for( FFmpeg_ProbeResult theProbeResult : allProbeInfoInstances )
		{
			videoCodecNames.add( theProbeResult.getVideoCodec() ) ;
		}
		return videoCodecNames ;
	}

	/**
	 * Return a MultiMap of all files that are not H265.
	 * The key is codec_name ("h264, mpeg2video, etc.), and the FFmpegProbeResult corresponds to that file.
	 * @return
	 */
	public List< FFmpeg_ProbeResult > findFilesThatNeedUpgrade( final List< FFmpeg_ProbeResult > allProbeInfoInstances )
	{		
		List< FFmpeg_ProbeResult > filesToUpgrade = new ArrayList< FFmpeg_ProbeResult >() ;
		
		for( FFmpeg_ProbeResult theProbeResult : allProbeInfoInstances )
		{
			if( theProbeResult.isVC1() )
			{
				filesToUpgrade.add( theProbeResult ) ;
			}
		}
		return filesToUpgrade ;
	}
}
