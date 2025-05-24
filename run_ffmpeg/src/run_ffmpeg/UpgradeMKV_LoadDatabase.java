package run_ffmpeg;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import com.mongodb.client.MongoCollection;

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
		final List< FFmpegProbeResult > allProbeInfoInstances = masMDB.getAllProbeInfoInstances() ;

		Set< String > videoCodecTypes = listAllVideoCodes( allProbeInfoInstances ) ;
		log.info( "Found video codecs: " + videoCodecTypes.toString() ) ;

		final List< FFmpegProbeResult > filesToUpgrade = findFilesThatNeedUpgrade( allProbeInfoInstances ) ;
		log.info( "Found " + filesToUpgrade.size() + " file(s) to upgrade" ) ;
		
//		for( FFmpegProbeResult theProbeResult : filesToUpgrade )
//		{
//			log.fine( "Upgrade: " + theProbeResult.getFileNameWithPath() ) ;
//		}
		log.info( "Adding files to database..." ) ;
		MongoCollection< FFmpegProbeResult > transcodeDatabaseJobHandle = masMDB.getAction_TranscodeMKVFileInfoCollection() ;
		transcodeDatabaseJobHandle.insertMany( filesToUpgrade ) ;

		log.info( "Shutdown." ) ;
	}

	/**
	 * Return a Set of all video codec names. This is useful to identify which types are in the library so we can
	 *  know which we need to evaluate as things continue to change.
	 */
	public Set< String > listAllVideoCodes( final List< FFmpegProbeResult > allProbeInfoInstances )
	{
		Set< String > videoCodecNames = new HashSet< String >() ;
		
		for( FFmpegProbeResult theProbeResult : allProbeInfoInstances )
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
	public List< FFmpegProbeResult > findFilesThatNeedUpgrade( final List< FFmpegProbeResult > allProbeInfoInstances )
	{		
		List< FFmpegProbeResult > filesToUpgrade = new ArrayList< FFmpegProbeResult >() ;
		
		for( FFmpegProbeResult theProbeResult : allProbeInfoInstances )
		{
			if( !theProbeResult.isH264() && !theProbeResult.isH265() )
			{
				filesToUpgrade.add( theProbeResult ) ;
			}
		}
		return filesToUpgrade ;
	}
}
