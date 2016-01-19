package org.podcastpedia.core.user;

import org.podcastpedia.common.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.*;

public class UserServiceImpl implements UserService {

    public static final int USER_NOT_YET_ENABLED = 0;
    public static final int USER_ENABLED = 1;
    public static final String ROLE_USER = "ROLE_USER";

    @Autowired
	UserDao userDao;

	@Override
    @Cacheable(value="users", key = "#userId")
	public List<Podcast> getSubscriptions(String userId) {

        List<Podcast> subscriptions = userDao.getSubscriptions(userId);

        //return only the last 3 episodes, ordered by publication date
        for(Podcast subscription: subscriptions){
            if(!subscription.getEpisodes().isEmpty() && subscription.getEpisodes().size() > 3){
                subscription.setEpisodes(subscription.getEpisodes().subList(0,3));
            }
        }

        return subscriptions;
	}

    @Override
    public List<Podcast> getPodcastsForPlaylist(String userId, String playlist) {

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("userId", userId);
        params.put("playlist", playlist);

        List<Podcast> subscriptions = userDao.getPodcastsForPlaylist(params);

        //return only the last 3 episodes, ordered by publication date
        for(Podcast subscription: subscriptions){
            if(!subscription.getEpisodes().isEmpty() && subscription.getEpisodes().size() > 3){
                subscription.setEpisodes(subscription.getEpisodes().subList(0,3));
            }
        }

        return subscriptions;
    }

	@Override
	public List<Episode> getLatestEpisodesFromSubscriptions(String username) {
		return userDao.getLatestEpisodesFromSubscriptions(username);
	}

    @Override
    public void submitUserForRegistration(User user) {
        user.setRegistrationDate(new Date());
        user.setEnabled(USER_NOT_YET_ENABLED);
        //if display name not introduced then use the name from the email address(the one before @)
        if(user.getDisplayName()==null){
            user.setDisplayName(user.getUsername());
        }
        user.setPassword(encryptPassword(user.getPassword()));
        userDao.addUser(user);
    }

    @Override
    public void updateUserForPasswordReset(User user) {
        //generate a new registration token
        user.setRegistrationToken(UUID.randomUUID().toString());
        //set the user on inactive, to be activated via email confirmation
        user.setEnabled(USER_NOT_YET_ENABLED);
        user.setPassword(encryptPassword(user.getPassword()));

        userDao.updateUserForPasswordReset(user);
    }

    @Override
    public boolean isExistingUser(String username) {
        User user = userDao.getUserByUsername(username);

        return user != null;
    }

    @Override
    @CacheEvict(value="users", key="#userId")
    public void subscribeToPodcast(String userId, int podcastId, String playlist) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("email", userId);
        params.put("podcastId", podcastId);
        params.put("playlist", playlist);

        userDao.subscribeToPodcast(params);
    }

    @Override
    @CacheEvict(value="users", key="#userId")
    public void unsubscribeFromPodcast(String userId, int podcastId) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("userId", userId);
        params.put("podcastId", podcastId);

        userDao.unsubscribeFromPodcast(params);
    }

    @Override
    @CacheEvict(value="users", key="#userId")
    public void removeFromPlaylist(String userId, Integer podcastId, String playlist) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("userId", userId);
        params.put("podcastId", podcastId);
        params.put("playlist", playlist);

        userDao.removeFromPlaylist(params);
    }



    @Override
    @CacheEvict(value="podcasts", key="#podcastId")
    public void votePodcast(final String username, final int podcastId, final int vote) {
        PodcastVote podcastVote = new PodcastVote();
        podcastVote.setUsername(username);
        podcastVote.setPodcastId(podcastId);
        podcastVote.setVote(vote);

        userDao.addPodcastVote(podcastVote);
    }

    @Override
    @CacheEvict(value = "podcasts", key = "T(java.lang.String).valueOf(#podcastId).concat('-').concat(#episodeId)")
    public void voteEpisode(String username, int podcastId, int episodeId, int vote) {
        EpisodeVote episodeVote = new EpisodeVote();
        episodeVote.setUsername(username);
        episodeVote.setPodcastId(podcastId);
        episodeVote.setEpisodeId(episodeId);
        episodeVote.setVote(vote);

        userDao.addEpisodeVote(episodeVote);
    }

    @Override
    public void enableUserAfterRegistration(String username, String registrationToken) {
        User user = new User();
        user.setUsername(username);
        user.setRole(ROLE_USER);
        user.setEnabled(USER_ENABLED);
        user.setRegistrationToken(registrationToken);

        userDao.addUserRole(user);
        userDao.enableUser(user);
    }

    @Override
    public void enableUserAfterPasswordForgotten(String username, String registrationToken) {
        User user = new User();
        user.setUsername(username);
        user.setEnabled(USER_ENABLED);
        user.setRegistrationToken(registrationToken);

        userDao.enableUser(user);
    }

    @Override
    public List<String> getPlaylistNames(String userId) {
        return userDao.getPlaylistsForUser(userId);
    }


    private String encryptPassword(String password) {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        String hashedPassword = passwordEncoder.encode(password);

        return hashedPassword;
    }



    public void setUserDao(UserDao userDao) {
        this.userDao = userDao;
    }
}
