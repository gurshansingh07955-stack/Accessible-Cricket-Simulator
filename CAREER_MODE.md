# My Player (career mode), part one

Home > My Player sits between Advanced Customisation and Settings. It opens two choices: **Start new career** and **Resume career**.
Up to five careers are kept (`CareerStore`, `careers.json`); with five, Start new career is switched off and says to delete one;
Resume career lists them, opens one, and can delete it (confirmed).

Starting a career asks, one page each: first and last name (one page, two boxes), age (16 to 30, 16 to 19 recommended), gender
(Male selected; Female switched off because the game has no women's cricket), jersey number (0 to 999), country (India, Pakistan,
Australia, England, New Zealand, South Africa), state, district club. The word on the screens is "state" for every country:
India lists its real states and union territories (each with the game's domestic team or teams: Gujarat has Gujarat, Baroda and
Saurashtra; Maharashtra has Maharashtra, Mumbai and Vidarbha; Telangana has Hyderabad; West Bengal has Bengal); the other countries list
exactly the teams that have domestic tournaments in the game (England's 18 counties, Australia's six states, New Zealand's six
associations, South Africa's provincial teams and franchises, Pakistan's regions). Each state has three or four district clubs, named
after real places of the state (`logic/Career.kt`, `CareerWorld`); only names, the players are generated later.

Not built yet (later parts): the club's randomly generated players with batting, bowling and fielding ratings and names typical of the
state, playing matches, progress, retirement.
