package com.amazon.ata.advertising.service.businesslogic;

import com.amazon.ata.advertising.service.dao.ReadableDao;
import com.amazon.ata.advertising.service.model.AdvertisementContent;
import com.amazon.ata.advertising.service.model.EmptyGeneratedAdvertisement;
import com.amazon.ata.advertising.service.model.GeneratedAdvertisement;
import com.amazon.ata.advertising.service.model.RequestContext;
import com.amazon.ata.advertising.service.targeting.TargetingEvaluator;
import com.amazon.ata.advertising.service.targeting.TargetingGroup;

import com.amazon.ata.advertising.service.targeting.predicate.TargetingPredicateResult;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.Opt;

import java.util.*;
import java.util.stream.Collectors;
import javax.inject.Inject;

/**
 * This class is responsible for picking the advertisement to be rendered.
 */
public class AdvertisementSelectionLogic {

    private static final Logger LOG = LogManager.getLogger(AdvertisementSelectionLogic.class);

    private final ReadableDao<String, List<AdvertisementContent>> contentDao;
    private final ReadableDao<String, List<TargetingGroup>> targetingGroupDao;
    private Random random = new Random();

    /**
     * Constructor for AdvertisementSelectionLogic.
     * @param contentDao Source of advertising content.
     * @param targetingGroupDao Source of targeting groups for each advertising content.
     */
    @Inject
    public AdvertisementSelectionLogic(ReadableDao<String, List<AdvertisementContent>> contentDao,
                                       ReadableDao<String, List<TargetingGroup>> targetingGroupDao) {
        this.contentDao = contentDao;
        this.targetingGroupDao = targetingGroupDao;
    }

    /**
     * Setter for Random class.
     * @param random generates random number used to select advertisements.
     */
    public void setRandom(Random random) {
        this.random = random;
    }

    /**
     * Gets all of the content and metadata for the marketplace and determines which content can be shown.  Returns the
     * eligible content with the highest click through rate.  If no advertisement is available or eligible, returns an
     * EmptyGeneratedAdvertisement.
     *
     * @param customerId - the customer to generate a custom advertisement for
     * @param marketplaceId - the id of the marketplace the advertisement will be rendered on
     * @return an advertisement customized for the customer id provided, or an empty advertisement if one could
     *     not be generated.
     */
    public GeneratedAdvertisement selectAdvertisement(String customerId, String marketplaceId) {
        GeneratedAdvertisement generatedAdvertisement = new EmptyGeneratedAdvertisement();
        TargetingEvaluator targetingEvaluator = new TargetingEvaluator(new RequestContext(customerId, marketplaceId));

        if (StringUtils.isEmpty(marketplaceId)) {
            LOG.warn("MarketplaceId cannot be null or empty. Returning empty ad.");
        } else {

            final SortedMap<Double, AdvertisementContent> clickRateTreeMap = new TreeMap<>(Comparator.reverseOrder());

            if (customerId == null || customerId.equals("")) {
                contentDao.get(marketplaceId).stream()
                        .forEach(content -> {
                            double maxRate = targetingGroupDao.get(content.getContentId()).stream()
                                    .mapToDouble(TargetingGroup::getClickThroughRate)
                                    .max()
                                    .orElse(0);

                            if (maxRate > 0) {
                                clickRateTreeMap.put(maxRate, content);
                            }
                        });
            } else {
                contentDao.get(marketplaceId).stream()
                        .forEach(content -> {
                            List<TargetingGroup> targetingGroups = targetingGroupDao.get(content.getContentId());
                            boolean isValidContent = targetingGroups.stream()
                                    .anyMatch(targetingGroup -> targetingEvaluator.evaluate(targetingGroup) == TargetingPredicateResult.TRUE);

                            if (isValidContent) {
                                Optional<Double> maxClickThroughRate = targetingGroups.stream()
                                        .map(TargetingGroup::getClickThroughRate)
                                        .max(Double::compare);

                                maxClickThroughRate.ifPresent(clickRate -> clickRateTreeMap.put(clickRate, content));
                            }
                        });
            }

            if (!clickRateTreeMap.isEmpty()) {
                AdvertisementContent randomAdvertisementContent = clickRateTreeMap.get(clickRateTreeMap.firstKey());
                generatedAdvertisement = new GeneratedAdvertisement(randomAdvertisementContent);
            }
        }

        return generatedAdvertisement;
    }
}
