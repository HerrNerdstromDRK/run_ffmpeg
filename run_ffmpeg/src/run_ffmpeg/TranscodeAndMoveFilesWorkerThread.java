package run_ffmpeg;

import java.util.List;
import java.util.logging.Logger;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

public class TranscodeAndMoveFilesWorkerThread extends run_ffmpegWorkerThread
{
	private transient TranscodeAndMoveFiles theController = null ;
	private transient MoviesAndShowsMongoDB masMDB = null ;
	protected transient MongoCollection< FFmpegProbeResult > probeInfoCollection = null ;
	private MongoCollection< MovieAndShowInfo > movieAndShowInfoCollection = null ;
	private List< TranscodeAndMoveFileInfo > filesToTranscode = null ;

	public TranscodeAndMoveFilesWorkerThread(
			TranscodeAndMoveFiles theController,
			MoviesAndShowsMongoDB masMDB,
			Logger log,
			Common common,
			List< TranscodeAndMoveFileInfo > filesToTranscode )
	{
		super( log, common ) ;

		assert( theController != null ) ;
		assert( masMDB != null ) ;
		assert( filesToTranscode != null ) ;

		this.theController = theController ;
		this.masMDB = masMDB ;
		this.filesToTranscode = filesToTranscode ;

		initObject() ;
	}

	public TranscodeAndMoveFilesWorkerThread( Logger log, Common common )
	{
		super( log, common ) ;
		initObject() ;
	}

	private void initObject()
	{
		if( null == masMDB )
		{
			// Copy constructor doesn't pass the masMDB
			masMDB = new MoviesAndShowsMongoDB() ;
		}
		probeInfoCollection = masMDB.getProbeInfoCollection() ;
		movieAndShowInfoCollection = masMDB.getMovieAndShowInfoCollection() ;
	}

	@Override
	public void run()
	{
		while( shouldKeepRunning() && !filesToTranscode.isEmpty() )
		{
			// Pre-condition: The MovieAndShowInfo for this show/movie already exists in the database, but not the probe infos.
			TranscodeAndMoveFileInfo transcodeAndMoveFileInfo = filesToTranscode.remove( 0 ) ;
			assert( transcodeAndMoveFileInfo != null ) ;

			TranscodeFile fileToTranscode = transcodeAndMoveFileInfo.fileToTranscode ;

			if( fileToTranscode.isTranscodeComplete() || fileToTranscode.isTranscodeInProgress() )
			{
				// Skipping due to previous or current transcode
				continue ;
			}

			// Create the transcode, mkv final, and mp4 final directories, if necessary.
			fileToTranscode.makeDirectories() ;

			TranscodeCommon tCommon = new TranscodeCommon(
					log,
					common,
					fileToTranscode ) ;

			// Transcode the file.
			fileToTranscode.setTranscodeInProgress();
			boolean transcodeSucceeded = tCommon.transcodeFile( fileToTranscode ) ;
			if( !transcodeSucceeded )
			{
				log.warning( "Transcode failed for file: " + fileToTranscode ) ;
				fileToTranscode.unSetTranscodeInProgress() ;
				continue ;
			}

			// transcode succeeded
			fileToTranscode.setTranscodeComplete() ;

			// Update the database with the final locations of the mkv and mp4 files.
			FFmpegProbeResult mkvProbeInfo = transcodeAndMoveFileInfo.mkvProbeInfo ;

			// Update the existing mkvProbeResult with new file locations and build a probe result for
			// the mp4 file in its final directory.
			// Will use this to update the MovieAndShowInfo database.
			mkvProbeInfo.setFileNameWithPath( fileToTranscode.getMKVFinalFileNameWithPath() ) ;
			mkvProbeInfo.setFileNameWithoutPath( fileToTranscode.getMKVFileName() ) ;
			mkvProbeInfo.setFileNameShort( Common.shortenFileName( fileToTranscode.getMKVFinalFileNameWithPath() ) ) ;

			// It would be clumsy to add code here to use findOne() followed by replaceOne() in the event a findOne() succeeds for this mkv,
			// so just delete it and insert the new mkv (2 calls instead of 3).
			probeInfoCollection.deleteOne( Filters.eq( "fileNameWithPath", fileToTranscode.getMKVFinalFileNameWithPath() ) ) ;
			probeInfoCollection.insertOne( mkvProbeInfo ) ;

			// Create the mp4 probe info.
			FFmpegProbeResult mp4ProbeInfo = common.ffprobeFile( fileToTranscode.getMP4OutputFile(), log ) ;

			// The system should be able to probe the resulting mp4 file since the transcode succeeded.
			assert( mp4ProbeInfo != null ) ;

			// Update the mp4ProbeInfo with the new file information.
			mp4ProbeInfo.setFileNameWithPath( fileToTranscode.getMP4FinalFileNameWithPath() ) ;
			mp4ProbeInfo.setFileNameWithoutPath( fileToTranscode.getMP4FileName() ) ;
			mp4ProbeInfo.setFileNameShort( Common.shortenFileName( fileToTranscode.getMP4FinalFileNameWithPath() ) ) ;

			// Now update the database for the mp4 file.
			probeInfoCollection.deleteOne( Filters.eq( "fileNameWithPath", fileToTranscode.getMP4FinalFileNameWithPath() ) ) ;
			probeInfoCollection.insertOne( mp4ProbeInfo ) ;

			// Update the MovieAndShowInfo with the new files and push to the database.
			MovieAndShowInfo movieAndShowInfo = transcodeAndMoveFileInfo.movieAndShowInfo ;
			movieAndShowInfo.addMKVFile( mkvProbeInfo ) ;
			movieAndShowInfo.addMP4File( mp4ProbeInfo ) ;
			movieAndShowInfo.makeReadyCorrelatedFilesList() ;
			movieAndShowInfoCollection.replaceOne( Filters.eq( "_id", movieAndShowInfo._id ), movieAndShowInfo ) ;

			// Move the mkv, srt, and mp4 files
			theController.moveFile( fileToTranscode ) ;
		} // while( shouldKeepRunning() && !filesToTranscode.isEmpty() )
	}

	public boolean shouldKeepRunning()
	{
		return theController.shouldKeepRunning() ;
	}
}
