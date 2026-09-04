/**
 * "Motherload" easter egg: an archaeology-themed Snake game rendered on a <canvas>.
 * Started/stopped by the p:dialog's onShow/onHide (siaSnakeInit/siaSnakeStop), triggered from
 * SearchBean#completeText when the user types "motherload" into the global search bar.
 */
(function () {
    'use strict';

    var CELL = 20;
    var COLS = 24;
    var ROWS = 24;
    var TICK_MS = 120;
    var ARTIFACTS = ['🏺', '🪙', '🦴', '⚗️', '🗿']; // 🏺 🪙 🦴 ⚗️ 🗿

    var canvas, ctx;
    var snake, dir, nextDir, food, score, running, loopId, keyListenerBound;

    function resetState() {
        snake = [
            {x: 12, y: 12},
            {x: 11, y: 12},
            {x: 10, y: 12}
        ];
        dir = {x: 1, y: 0};
        nextDir = dir;
        score = 0;
        running = true;
        placeFood();
        updateScoreDisplay();
    }

    function placeFood() {
        var cell;
        do {
            cell = {x: Math.floor(Math.random() * COLS), y: Math.floor(Math.random() * ROWS)};
        } while (snake.some(function (s) { return s.x === cell.x && s.y === cell.y; }));
        food = {x: cell.x, y: cell.y, symbol: ARTIFACTS[Math.floor(Math.random() * ARTIFACTS.length)]};
    }

    function updateScoreDisplay() {
        var el = document.getElementById('siaSnakeScore');
        if (el) el.textContent = score;
    }

    function tick() {
        if (!running) return;
        dir = nextDir;
        var head = {x: snake[0].x + dir.x, y: snake[0].y + dir.y};

        var hitsWall = head.x < 0 || head.x >= COLS || head.y < 0 || head.y >= ROWS;
        var hitsSelf = snake.some(function (s) { return s.x === head.x && s.y === head.y; });
        if (hitsWall || hitsSelf) {
            gameOver();
            return;
        }

        snake.unshift(head);
        if (head.x === food.x && head.y === food.y) {
            score += 10;
            updateScoreDisplay();
            placeFood();
        } else {
            snake.pop();
        }
        draw();
    }

    function draw() {
        ctx.fillStyle = '#e8dcc3';
        ctx.fillRect(0, 0, canvas.width, canvas.height);

        ctx.strokeStyle = 'rgba(90,70,50,0.08)';
        var i, j;
        for (i = 0; i <= COLS; i++) {
            ctx.beginPath();
            ctx.moveTo(i * CELL, 0);
            ctx.lineTo(i * CELL, canvas.height);
            ctx.stroke();
        }
        for (j = 0; j <= ROWS; j++) {
            ctx.beginPath();
            ctx.moveTo(0, j * CELL);
            ctx.lineTo(canvas.width, j * CELL);
            ctx.stroke();
        }

        ctx.font = (CELL - 2) + 'px serif';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(food.symbol, food.x * CELL + CELL / 2, food.y * CELL + CELL / 2);

        snake.forEach(function (seg, idx) {
            ctx.fillStyle = idx === 0 ? '#8a4a2c' : '#b97a4a';
            ctx.fillRect(seg.x * CELL + 1, seg.y * CELL + 1, CELL - 2, CELL - 2);
        });
        ctx.font = (CELL - 4) + 'px serif';
        ctx.fillText('⛏️', snake[0].x * CELL + CELL / 2, snake[0].y * CELL + CELL / 2); // ⛏️
    }

    function gameOver() {
        running = false;
        clearInterval(loopId);
        if (typeof saveSnakeScoreRemote === 'function') {
            saveSnakeScoreRemote([{name: 'score', value: score}]);
        } else {
            window.siaSnakeShowLeaderboard();
        }
    }

    /** Fades the board out and the ranking in; called once the score request completes. */
    window.siaSnakeShowLeaderboard = function () {
        var area = document.getElementById('siaSnakeGameArea');
        var board = document.getElementById('siaSnakeLeaderboard');
        if (!area || !board) return;
        area.style.opacity = '0';
        setTimeout(function () {
            area.style.display = 'none';
            board.style.display = 'block';
            requestAnimationFrame(function () {
                board.style.opacity = '1';
            });
        }, 600);
    };

    /** "Rejouer": fades the ranking out, restores the board, and starts a fresh run. */
    window.siaSnakeRestart = function () {
        var area = document.getElementById('siaSnakeGameArea');
        var board = document.getElementById('siaSnakeLeaderboard');
        if (board) board.style.opacity = '0';
        setTimeout(function () {
            if (board) board.style.display = 'none';
            if (area) {
                area.style.display = 'block';
                requestAnimationFrame(function () {
                    area.style.opacity = '1';
                });
            }
            window.siaSnakeInit();
        }, 600);
    };

    function onKeyDown(e) {
        if (!running) return;
        var key = e.key ? e.key.toLowerCase() : '';
        var d = null;
        if (key === 'arrowup' || key === 'z') d = {x: 0, y: -1};
        else if (key === 'arrowdown' || key === 's') d = {x: 0, y: 1};
        else if (key === 'arrowleft' || key === 'q') d = {x: -1, y: 0};
        else if (key === 'arrowright' || key === 'd') d = {x: 1, y: 0};
        if (!d) return;
        if (d.x === -dir.x && d.y === -dir.y) return; // no direct reversal
        nextDir = d;
        e.preventDefault();
    }

    window.siaSnakeInit = function () {
        canvas = document.getElementById('siaSnakeCanvas');
        if (!canvas) return;
        ctx = canvas.getContext('2d');

        var area = document.getElementById('siaSnakeGameArea');
        var board = document.getElementById('siaSnakeLeaderboard');
        if (board) { board.style.display = 'none'; board.style.opacity = '0'; }
        if (area) { area.style.display = 'block'; area.style.opacity = '1'; }

        if (!keyListenerBound) {
            document.addEventListener('keydown', onKeyDown);
            keyListenerBound = true;
        }

        resetState();
        draw();
        clearInterval(loopId);
        loopId = setInterval(tick, TICK_MS);
    };

    window.siaSnakeStop = function () {
        running = false;
        clearInterval(loopId);
        if (keyListenerBound) {
            document.removeEventListener('keydown', onKeyDown);
            keyListenerBound = false;
        }
    };
})();
