package org.podcastpedia.core.searching;

import org.apache.log4j.Logger;
import org.podcastpedia.common.domain.Episode;
import org.podcastpedia.common.domain.Podcast;
import org.podcastpedia.common.types.OrderByOption;
import org.podcastpedia.common.types.SearchModeType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class SearchServiceImpl implements SearchService {

	protected static Logger LOG = Logger.getLogger(SearchServiceImpl.class);

	@Autowired
	private SearchDao searchDao;

	public void setSearchDao(SearchDao searchDao) {
		this.searchDao = searchDao;
	}

	/** list of international tex for "return all" - used in quick search bar **/
	private static final String[] textGetAllPodcastsI18N = {"Search...", "Căutare...",
			"Buscar...", "Suchen...", "Rechercher..."};

	@Cacheable(value="searchResults")
	public SearchResult getResultsForSearchCriteria(SearchData searchData) throws UnsupportedEncodingException {

		SearchResult response = new SearchResult();
		if(searchData.getCategId()!=null && searchData.getCategId().isEmpty()){
			searchData.setCategId(null);
		}
		searchData.setFirstItemOnPage((searchData.getCurrentPage()-1) * searchData.getNumberResultsPerPage());

		LOG.debug("Received request in service to invoke dao to make a search in podcasts " +
				" for text : " + searchData.getQueryText());

		buildSQLQuerySearchText(searchData);

		response.setNumberOfItemsPerPage(searchData.getNumberResultsPerPage());
		response.setCurrentPage(searchData.getCurrentPage());

		if( isTargetPodcasts(searchData) ){
            List<Podcast> podcasts = searchDao.getPodcastsForSearchCriteria(searchData);
            response.setResults(mapPodcastsToResults(podcasts));
		} else {
            List<Episode> episodes = searchDao.getEpisodesForSearchCriteria(searchData);
            response.setResults(mapEpisodesToResults(episodes));
		}

		return response;
	}

    private List<Result> mapPodcastsToResults(List<Podcast> podcasts) {
        List<Result> results = new ArrayList<>();
        for(Podcast podcast : podcasts){
            Result result = new Result();
            result.setPodcastId(podcast.getPodcastId());
            result.setIsEpisode(false);
            result.setTitle(podcast.getTitle());
            result.setPublicationDate(podcast.getPublicationDate());
            result.setMediaType(podcast.getMediaType());
            result.setDescription(podcast.getDescription());
            result.setMediaUrl(podcast.getLastEpisodeMediaUrl());
            if(podcast.getIdentifier()!=null){
                result.setRelativeLink("/" + podcast.getIdentifier());
            } else {
                result.setRelativeLink("/podcasts/" + podcast.getPodcastId() + "/" + podcast.getTitleInUrl());
            }

            results.add(result);
        }

        return results;
    }

    private List<Result> mapEpisodesToResults(List<Episode> episodes) {
        List<Result> results = new ArrayList<>();
        for(Episode episode : episodes){
            Result result = new Result();
            result.setPodcastId(episode.getPodcastId());
            result.setEpisodeId(episode.getEpisodeId());
            result.setIsEpisode(true);
            result.setTitle(episode.getTitle());
            result.setPublicationDate(episode.getPublicationDate());
            result.setMediaType(episode.getMediaType());
            StringBuilder episodeRelativeLink = new StringBuilder();
            episodeRelativeLink.append("/podcasts/").append(episode.getPodcastId()).append("/").append(episode.getPodcast().getTitleInUrl());
            episodeRelativeLink.append("/episodes/").append(episode.getEpisodeId()).append("/").append(episode.getTitleInUrl());
            result.setRelativeLink(episodeRelativeLink.toString());
            result.setDescription(episode.getDescription());
            result.setMediaUrl(episode.getMediaUrl());

            results.add(result);
        }

        return results;
    }


    private boolean isTargetPodcasts(SearchData searchData) {
		return searchData.getSearchTarget() !=null && searchData.getSearchTarget().equals("podcasts");
	}

	private void buildSQLQuerySearchText(SearchData searchData){
		if(searchData.getSearchMode().equals(SearchModeType.NATURAL_MODE.getValue())) {
			//if it is the default "return all podcasts" as query text than all podcasts will be returned
			//QueryText can be null when the request is coming from quick search on the start page
			if(isDefaultSearchText(searchData)) {
				searchData.setQueryText(null);
				//if no order by is selected in this case
				if(searchData.getOrderBy() == null){
					//this cannot happen from quick search filters from start page
					searchData.setOrderBy(OrderByOption.PUBLICATION_DATE);
				}
			} else {
				//if some words are introduced then we should decide the full text indexing to bring the most relevant in front
				searchData.setOrderBy(null);
			}
			// else it means the user has given some input text for natural mode search
		}

        //in boolean search mode (see - https://dev.mysql.com/doc/refman/5.6/en/fulltext-boolean.html)
        // the corresponding operators are provided by the client of the api
		if(searchData.getSearchMode().equals(SearchModeType.BOOLEAN_MODE.getValue())){

			StringBuilder queryTextBuffer = new StringBuilder();

			//start with the all of these words part
			if(searchData.getAllTheseWords()!=null
					&& !searchData.getAllTheseWords().trim().equals("")){
				String[] allWords = searchData.getAllTheseWords().split(" ");
				for(String s: allWords){
					queryTextBuffer.append(" ").append(s);
				}
			}
			// none of these words
			if(searchData.getNoneOfTheseWords()!=null
					&& !searchData.getNoneOfTheseWords().trim().equals("")){
				String[] noneOfWords = searchData.getNoneOfTheseWords().split(" ");
				for(String s: noneOfWords){
					queryTextBuffer.append(" ").append(s);
				}
			}
			//any of these words - is simply just added
			if(searchData.getAnyOfTheseWords()!=null
					&& !searchData.getAnyOfTheseWords().trim().equals("")){
				queryTextBuffer.append(" ").append(searchData.getAnyOfTheseWords());
			}
			//exact phrase
			if(searchData.getExactPhrase()!=null
					&& !searchData.getExactPhrase().trim().equals("")){
                //the client will provided only the exact phrase between quotes, and we use the plus sign (+) to force its existence
				queryTextBuffer.append(" +").append(searchData.getExactPhrase());
			}

			//set the queryText
			searchData.setQueryText(queryTextBuffer.toString());
		}
	}

	private boolean isDefaultSearchText(SearchData searchData) {
		return searchData.getQueryText() == null || Arrays.asList(textGetAllPodcastsI18N).contains(searchData.getQueryText().trim());
	}


}