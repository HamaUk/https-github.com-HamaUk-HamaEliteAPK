const express = require('express');
const axios = require('axios');
const cheerio = require('cheerio');
const cors = require('cors');

const app = express();
app.use(cors());

app.get('/api/matches', async (req, res) => {
    try {
        const { data } = await axios.get('https://www.ysscores.com/en/today_matches', {
            headers: {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
                'Accept-Language': 'en-US,en;q=0.9',
            }
        });

        const $ = cheerio.load(data);
        const leagues = [];

        $('.matches-wrapper').each((i, leagueElem) => {
            const leagueName = $(leagueElem).attr('champ_title');
            const leagueIcon = $(leagueElem).attr('champ_img');

            if (!leagueName) return;

            const matches = [];

            $(leagueElem).find('a.ajax-match-item').each((j, matchElem) => {
                const $match = $(matchElem);
                
                const homeName = $match.attr('home_name');
                const awayName = $match.attr('away_name');
                const homeImage = $match.attr('home_image');
                const awayImage = $match.attr('away_image');

                const homeScore = $match.find('.first-team-result').text().trim() || '-';
                const awayScore = $match.find('.second-team-result').text().trim() || '-';

                let status = $match.find('.result-status-text').first().text().trim();
                if (!status) {
                    // Try getting the ended / other status
                    status = $match.find('.live-match-status').text().trim();
                }

                // Check for live match time
                let matchTime = $match.find('.number span').first().text().trim();
                if (!status && matchTime) {
                   status = matchTime; 
                }
                
                // Fallback for ended matches
                if (!status) {
                     status = $match.find('.match-inner-progress-wrap').children('span').text().trim();
                }

                matches.push({
                    homeTeam: homeName,
                    awayTeam: awayName,
                    homeLogo: homeImage,
                    awayLogo: awayImage,
                    score: `${homeScore} - ${awayScore}`,
                    status: status || 'Upcoming / Ended'
                });
            });

            leagues.push({
                league: leagueName,
                leagueIcon: leagueIcon,
                matches: matches
            });
        });

        res.json(leagues);
    } catch (error) {
        console.error("Scraping error:", error.message);
        res.status(500).json({ error: "Failed to scrape match data" });
    }
});

const PORT = 3000;
app.listen(PORT, () => {
    console.log(`Sports API is running at http://localhost:${PORT}/api/matches`);
});
